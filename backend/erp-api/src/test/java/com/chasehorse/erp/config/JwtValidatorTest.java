package com.chasehorse.erp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidatorTest {
    private static final String ISS = "https://abc.supabase.co/auth/v1";
    private final OAuth2TokenValidator<Jwt> validator = JwtConfig.validator(ISS);

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "ES256")
                .claim("iss", ISS).subject("a0000000-0000-0000-0000-000000000011")
                .claim("aud", List.of("authenticated")).claim("role", "authenticated")
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(600));
    }

    @Test
    void acceptsAStandardSupabaseUserToken() {
        assertThat(validator.validate(base().build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsTheAnonKeyRole() {
        assertThat(validator.validate(base().claim("role", "anon").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        assertThat(validator.validate(base().claim("aud", List.of("somebody-else")).build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(base().claim("iss", "https://evil.example/auth/v1").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredTokens() {
        assertThat(validator.validate(base().issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build()).hasErrors()).isTrue();
    }
}
