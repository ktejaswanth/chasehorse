package com.chasehorse.erp.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Supabase Auth access tokens.
 *
 *  - Asymmetric projects (default): signature checked against the project's public JWKS
 *    at {SUPABASE_URL}/auth/v1/.well-known/jwks.json (ES256 / RS256). No secret needed.
 *  - Legacy HS256 projects: set SUPABASE_JWT_SECRET.
 *  - Always checked: signature, expiry, issuer ({SUPABASE_URL}/auth/v1), audience
 *    "authenticated" and role claim "authenticated" (this rejects the public anon key).
 *  - If Supabase is not configured at all, EVERY token is rejected (fail closed).
 */
@Configuration
public class JwtConfig {
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    JwtDecoder jwtDecoder(AppProperties props) {
        AppProperties.Supabase sb = props.supabase();
        if (!sb.hasUrl()) {
            log.warn("SUPABASE_URL is not set: all authenticated requests will be rejected until it is configured.");
            return token -> {
                throw new JwtException("Authentication is not configured on this server.");
            };
        }

        NimbusJwtDecoder decoder;
        if (sb.jwtSecret() != null && !sb.jwtSecret().isBlank()) {
            decoder = NimbusJwtDecoder
                    .withSecretKey(new SecretKeySpec(sb.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else {
            decoder = NimbusJwtDecoder
                    .withJwkSetUri(sb.issuer() + "/.well-known/jwks.json")
                    .jwsAlgorithm(SignatureAlgorithm.ES256)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        }
        decoder.setJwtValidator(validator(sb.issuer()));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> {
                    boolean audOk = jwt.getAudience() != null && jwt.getAudience().contains("authenticated");
                    boolean roleOk = "authenticated".equals(jwt.getClaimAsString("role"));
                    boolean subOk = jwt.getSubject() != null && !jwt.getSubject().isBlank();
                    return audOk && roleOk && subOk
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error("invalid_token", "Token is not a Supabase user session token.", null));
                }));
    }
}
