package com.chasehorse.erp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.chasehorse.erp.common.ApiException;
import com.chasehorse.erp.security.AccountStatus;
import com.chasehorse.erp.security.Actor;
import com.chasehorse.erp.security.ActorService;
import com.chasehorse.erp.security.AppRole;
import com.chasehorse.erp.support.ApiWebTest;
import com.chasehorse.erp.user.UserController;
import com.chasehorse.erp.user.UserDtos.Me;
import com.chasehorse.erp.user.UserService;

@WebMvcTest(UserController.class)
@ApiWebTest
class ApiSecurityTest {
    private static final UUID ID = UUID.fromString("a0000000-0000-0000-0000-000000000011");

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    ActorService actors;
    @MockitoBean
    UserService users;

    @BeforeEach
    void actor() {
        when(actors.load(any())).thenReturn(new Actor(ID, "e@t.test", AccountStatus.ACTIVE, Set.of(AppRole.EMPLOYEE)));
    }

    @Test
    void noTokenIsRejectedWith401Problem() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("bad"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSessionReachesTheController() throws Exception {
        when(users.me(any())).thenReturn(new Me(ID, "e@t.test", "Emp", null, "active", List.of("employee"), true, false));
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.roles[0]").value("employee"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void serviceForbiddenBecomes403WithoutLeakingDetails() throws Exception {
        when(users.me(any())).thenThrow(ApiException.forbidden("nope"));
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void unexpectedErrorIs500WithGenericMessage() throws Exception {
        when(users.me(any())).thenThrow(new IllegalStateException("secret table name leaked?"));
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(ID.toString()))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @Test
    void unknownJsonFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/me/registration").with(jwt().jwt(j -> j.subject(ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Asha\",\"roles\":[\"admin\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationFailuresAre422WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/me/registration").with(jwt().jwt(j -> j.subject(ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.displayName").exists());
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/me").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/me").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
