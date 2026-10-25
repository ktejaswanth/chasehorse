package com.chasehorse.erp.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;

class PgErrorsTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static UncategorizedSQLException wrap(String message, String state) {
        return new UncategorizedSQLException("task", "select 1", new SQLException(message, state));
    }

    @Test
    void businessRuleStatesMapToHttpStatusesAndKeepTheMessage() {
        assertThat(PgErrors.translate(wrap("not allowed", "CH403")).orElseThrow().status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(PgErrors.translate(wrap("missing", "CH404")).orElseThrow().status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(PgErrors.translate(wrap("task is no longer available", "CH409")).orElseThrow().getMessage())
                .isEqualTo("task is no longer available");
        assertThat(PgErrors.translate(wrap("bad value", "CH422")).orElseThrow().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void constraintAndDeadlockStatesAreMappedWithGenericText() {
        assertThat(PgErrors.translate(wrap("duplicate key value violates unique constraint \"x_uq\"", "23505")).orElseThrow().getMessage())
                .doesNotContain("x_uq").contains("already exists");
        assertThat(PgErrors.translate(wrap("deadlock", "40P01")).orElseThrow().status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(PgErrors.translate(wrap("bad input", "22P02")).orElseThrow().status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownStatesAreNotTranslated() {
        assertThat(PgErrors.translate(wrap("boom", "XX000"))).isEmpty();
        assertThat(PgErrors.translate(new RuntimeException("no sql here"))).isEmpty();
    }

    @Test
    void handlerNeverReturnsRawDatabaseTextForUnknownErrors() {
        var res = handler.data(wrap("relation \"app.secret_table\" does not exist", "42P01"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getDetail()).isEqualTo("An unexpected error occurred.").doesNotContain("secret_table");
    }

    @Test
    void handlerReturnsTheBusinessMessageForConflicts() {
        var res = handler.data(wrap("task is no longer available", "CH409"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().getDetail()).isEqualTo("task is no longer available");
        assertThat(res.getBody().getProperties()).containsEntry("code", "conflict");
    }
}
