package com.chasehorse.erp.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed configuration. Secrets come from the environment only; nothing here has a real default secret. */
@ConfigurationProperties(prefix = "chasehorse")
public record AppProperties(Supabase supabase, Cors cors, Limits limits) {

    public record Supabase(String url, String jwtSecret, String serviceKey, String evidenceBucket, int signedUrlSeconds) {
        public boolean hasUrl() {
            return url != null && !url.isBlank();
        }

        public boolean hasServiceKey() {
            return hasUrl() && serviceKey != null && !serviceKey.isBlank();
        }

        /** Base URL without a trailing slash. */
        public String baseUrl() {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }

        public String issuer() {
            return baseUrl() + "/auth/v1";
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Limits(int maxPageSize) {
    }
}
