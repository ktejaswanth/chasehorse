package com.chasehorse.erp.web;

import java.sql.SQLException;
import java.util.Optional;

import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;

import com.chasehorse.erp.common.ApiException;

/**
 * Translates database errors into safe API errors.
 *
 * The database raises business-rule violations with custom SQLSTATEs:
 * CH403 forbidden, CH404 not found, CH409 conflict, CH422 validation. Their
 * messages are written for people and are returned. Any other database error is
 * mapped to a generic message; raw SQL text, table names or stack traces are never returned.
 */
public final class PgErrors {
    private PgErrors() {
    }

    public static Optional<SQLException> find(Throwable t) {
        int guard = 0;
        while (t != null && guard++ < 20) {
            if (t instanceof SQLException s) {
                return Optional.of(s);
            }
            t = t.getCause();
        }
        return Optional.empty();
    }

    public static Optional<ApiException> translate(Throwable t) {
        return find(t).flatMap(PgErrors::fromSql);
    }

    static Optional<ApiException> fromSql(SQLException e) {
        String state = e.getSQLState();
        if (state == null) {
            return Optional.empty();
        }
        String msg = message(e);
        return switch (state) {
            case "CH403" -> Optional.of(new ApiException(HttpStatus.FORBIDDEN, "forbidden", msg));
            case "CH404" -> Optional.of(new ApiException(HttpStatus.NOT_FOUND, "not_found", msg));
            case "CH409" -> Optional.of(new ApiException(HttpStatus.CONFLICT, "conflict", msg));
            case "CH422" -> Optional.of(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", msg));
            case "23505" -> Optional.of(new ApiException(HttpStatus.CONFLICT, "conflict", "This record already exists or conflicts with an existing one."));
            case "23503" -> Optional.of(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "A referenced record does not exist or is still in use."));
            case "23514", "23502" -> Optional.of(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "A value is missing or breaks a data rule."));
            case "22P02", "22007", "22008" -> Optional.of(new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "A value has an invalid format."));
            case "40001", "40P01" -> Optional.of(new ApiException(HttpStatus.CONFLICT, "conflict", "The request conflicted with another update. Please retry."));
            default -> state.startsWith("22") ? Optional.of(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "A value is out of range."))
                    : Optional.empty();
        };
    }

    private static String message(SQLException e) {
        if (e instanceof PSQLException p && p.getServerErrorMessage() != null && p.getServerErrorMessage().getMessage() != null) {
            return p.getServerErrorMessage().getMessage();
        }
        return e.getMessage() == null ? "Request rejected." : e.getMessage();
    }
}
