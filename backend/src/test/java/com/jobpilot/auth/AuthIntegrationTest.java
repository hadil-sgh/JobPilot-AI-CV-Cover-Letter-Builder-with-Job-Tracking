package com.jobpilot.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jobpilot.AbstractIntegrationTest;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode register(String email) throws Exception {
        String body = postJson("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"s3cret-pass\",\"fullName\":\"Test User\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        register(email);

        String login = postJson("/api/auth/login", "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"s3cret-pass\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String access = json.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = uniqueEmail();
        register(email);
        postJson("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"another-pass\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputIs400() throws Exception {
        postJson("/api/auth/register", "{\"email\":\"not-an-email\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        String email = uniqueEmail();
        register(email);
        postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"wrong-pass\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesAll() throws Exception {
        String first = register(uniqueEmail()).get("refreshToken").asText();

        String rotated = postJson("/api/auth/refresh", "{\"refreshToken\":\"" + first + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = json.readTree(rotated).get("refreshToken").asText();

        // Replaying the old token is treated as theft...
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + first + "\"}").andExpect(status().isUnauthorized());
        // ...so the newer token is revoked too.
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + second + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = register(uniqueEmail()).get("refreshToken").asText();
        postJson("/api/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isNoContent());
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isUnauthorized());
    }
}
