package com.chasehorse.erp.common;

import org.springframework.http.HttpStatus;

/** An error that is safe to show to the caller. Never wrap internal details in one of these. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException forbidden(String m) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", m);
    }

    public static ApiException notFound(String m) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", m);
    }

    public static ApiException conflict(String m) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", m);
    }

    public static ApiException invalid(String m) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", m);
    }

    public static ApiException badRequest(String m) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", m);
    }

    public static ApiException notConfigured(String m) {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "not_configured", m);
    }

    public static ApiException unavailable(String m) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable", m);
    }
}
