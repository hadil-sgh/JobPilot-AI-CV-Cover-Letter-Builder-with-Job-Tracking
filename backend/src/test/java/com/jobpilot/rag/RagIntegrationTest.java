package com.jobpilot.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jobpilot.AbstractIntegrationTest;

/** Chunking + embedding + pgvector search end to end (fake embeddings, real pgvector). */
class RagIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"r-" + UUID.randomUUID() + "@example.com\",\"password\":\"s3cret-pass\"}"))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    private JsonNode call(MockHttpServletRequestBuilder req) throws Exception {
        return json.readTree(mvc.perform(auth(req)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void addItem(String itemJson) throws Exception {
        mvc.perform(auth(post("/api/profile/items")).contentType(MediaType.APPLICATION_JSON).content(itemJson))
                .andExpect(status().isCreated());
    }

    /** The indexer runs asynchronously after commit; wait until it has caught up. */
    private int waitForChunks(int expected) throws Exception {
        int n = -1;
        for (int i = 0; i < 50; i++) {
            n = call(get("/api/profile/index")).get("chunks").asInt();
            if (n == expected) {
                return n;
            }
            Thread.sleep(100);
        }
        return n;
    }

    @Test
    void editsAreIndexedAutomaticallyAndSearchFindsTheRightChunk() throws Exception {
        addItem("""
                {"type":"EXPERIENCE","title":"Backend Intern","organization":"Vermeg",
                 "bullets":["Built REST APIs with Spring Boot and PostgreSQL","Wrote JUnit tests"]}""");
        addItem("""
                {"type":"EXPERIENCE","title":"Freelance web developer",
                 "bullets":["Delivered Angular websites for small businesses"]}""");
        addItem("{\"type\":\"SKILL\",\"title\":\"Tools\",\"tags\":[\"Docker\",\"Git\",\"Kubernetes\"]}");

        assertThat(waitForChunks(3)).isEqualTo(3);

        JsonNode hits = call(get("/api/profile/search").param("q", "Spring Boot REST APIs"));
        assertThat(hits.get(0).get("content").asText()).contains("Vermeg");
        assertThat(hits.get(0).get("type").asText()).isEqualTo("EXPERIENCE");
        assertThat(hits.get(0).get("similarity").asDouble()).isGreaterThan(hits.get(1).get("similarity").asDouble());

        JsonNode docker = call(get("/api/profile/search").param("q", "Docker and Kubernetes"));
        assertThat(docker.get(0).get("type").asText()).isEqualTo("SKILL");
    }

    @Test
    void manualReindexAndSearchValidation() throws Exception {
        addItem("{\"type\":\"SKILL\",\"title\":\"Languages\",\"tags\":[\"Java\"]}");
        assertThat(call(post("/api/profile/reindex")).get("chunks").asInt()).isEqualTo(1);

        mvc.perform(auth(get("/api/profile/search").param("q", "x".repeat(501)))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/profile/search").param("q", "java").param("k", "50"))).andExpect(status().isBadRequest());
    }

    @Test
    void searchIsScopedToTheCallersProfile() throws Exception {
        addItem("{\"type\":\"SKILL\",\"title\":\"Secret\",\"tags\":[\"Cobol\",\"Fortran\"]}");
        waitForChunks(1);

        String otherBody = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"o-" + UUID.randomUUID() + "@example.com\",\"password\":\"s3cret-pass\"}"))
                .andReturn().getResponse().getContentAsString();
        String other = json.readTree(otherBody).get("accessToken").asText();

        String hits = mvc.perform(get("/api/profile/search").param("q", "Cobol Fortran")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(hits)).isEmpty();
    }
}
