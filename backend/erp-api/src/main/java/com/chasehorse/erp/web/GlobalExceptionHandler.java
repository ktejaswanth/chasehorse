package com.chasehorse.erp.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.chasehorse.erp.common.ApiException;

import jakarta.validation.ConstraintViolationException;

/** One place that turns every failure into a consistent, non-leaky RFC 7807 response. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setProperty("code", code);
        p.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return p;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(problem(e.status(), e.code(), e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(HttpStatus.FORBIDDEN, "forbidden", "Access denied."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> data(DataAccessException e) {
        return fromThrowable(e);
    }

    @ExceptionHandler(java.sql.SQLException.class)
    ResponseEntity<ProblemDetail> sql(java.sql.SQLException e) {
        return fromThrowable(e);
    }

    private ResponseEntity<ProblemDetail> fromThrowable(Throwable e) {
        return PgErrors.translate(e)
                .map(a -> ResponseEntity.status(a.status()).body(problem(a.status(), a.code(), a.getMessage())))
                .orElseGet(() -> {
                    log.error("Unexpected database error", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred."));
                });
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraint(ConstraintViolationException e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "Request validation failed.");
        Map<String, String> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        p.setProperty("fields", fields);
        return ResponseEntity.unprocessableEntity().body(p);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> mismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "bad_request", "Parameter '" + e.getName() + "' has an invalid value."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "Request validation failed.");
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        p.setProperty("fields", fields);
        return ResponseEntity.unprocessableEntity().body(p);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> fallback(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred."));
    }
}
