package com.chasehorse.erp.web;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** Writes an RFC 7807 problem body for errors raised outside Spring MVC (security filters). */
@Component
public class ProblemWriter {
    private final ObjectMapper mapper;

    public ProblemWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse res, int status, String code, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), java.util.Map.of(
                "type", "about:blank",
                "title", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(),
                "status", status,
                "detail", detail,
                "code", code,
                "requestId", String.valueOf(MDC.get(RequestIdFilter.MDC_KEY))));
    }
}
