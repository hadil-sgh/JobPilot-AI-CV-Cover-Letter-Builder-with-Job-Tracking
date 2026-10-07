package com.jobpilot.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jobpilot.AbstractIntegrationTest;
import com.jobpilot.generation.llm.GenerationLlm;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.llm.GenerationModels.RefBullets;
import com.jobpilot.generation.llm.GenerationModels.SkillGroupOut;
import com.jobpilot.generation.llm.GenerationModels.Verdict;
import com.jobpilot.job.JobAnalysis;
import com.jobpilot.job.JobAnalyzer;

/** Full flow: profile → job analysis → application → async generation → documents (LLM mocked). */
class GenerationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    JobAnalyzer analyzer;

    @MockitoBean
    GenerationLlm llm;

    private String token;

    static final CvDraftOut HALLUCINATED = new CvDraftOut("Backend developer with Kubernetes experience.",
            List.of(new RefBullets("E1", List.of("Deployed Spring Boot APIs on Kubernetes"))), List.of(),
            List.of(new SkillGroupOut("Tools", List.of("Docker", "Kubernetes"))));
    static final CvDraftOut CLEAN = new CvDraftOut("Backend developer who built Spring Boot REST APIs at Vermeg.",
            List.of(new RefBullets("E1", List.of("Built Spring Boot REST APIs on PostgreSQL"))), List.of(),
            List.of(new SkillGroupOut("Tools", List.of("Docker", "Git"))));
    static final LetterDraftOut LETTER = new LetterDraftOut("Dear Hiring Manager,",
            List.of("At Vermeg I built Spring Boot REST APIs.", "I would enjoy doing the same at Nexa Fintech."),
            "Kind regards,");

    @BeforeEach
    void setUp() throws Exception {
        token = register();
        when(analyzer.analyze(anyString())).thenReturn(new JobAnalysis("Backend Developer", "Nexa Fintech", "en", "junior",
                List.of("Spring Boot REST APIs", "Knowledge of Kubernetes"), List.of(), List.of("Spring Boot", "Docker"),
                List.of("Build APIs"), "friendly"));
        when(llm.judgeMatch(any(), any())).thenReturn(List.of(new Verdict(1, "yes"), new Verdict(2, "yes")));
        // doReturn style: stubbing must not invoke the earlier argThat matchers with null arguments.
        doReturn(HALLUCINATED).when(llm).writeCv(any(), any(), argThat(f -> f == null || f.isEmpty()), anyDouble());
        doReturn(CLEAN).when(llm).writeCv(any(), any(), argThat(f -> f != null && !f.isEmpty()), anyDouble());
        when(llm.writeLetter(any(), any(), anyList(), anyDouble())).thenReturn(LETTER);
    }

    // ---------- helpers ----------

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"g-" + UUID.randomUUID() + "@example.com\",\"password\":\"s3cret-pass\",\"fullName\":\"Sami Ben Ali\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder r, String t) {
        return r.header("Authorization", "Bearer " + t);
    }

    private JsonNode call(MockHttpServletRequestBuilder r, int expectedStatus) throws Exception {
        String body = mvc.perform(auth(r, token)).andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private JsonNode postJson(String url, Object body, int expected) throws Exception {
        return call(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), expected);
    }

    private String fillProfileAndCreateApplication() throws Exception {
        postJson("/api/profile/items", Map.of("type", "EXPERIENCE", "title", "Software Engineering Intern",
                "organization", "Vermeg", "startDate", "2024-02-01", "endDate", "2024-08-01",
                "bullets", List.of("Built Spring Boot REST APIs on PostgreSQL")), 201);
        postJson("/api/profile/items", Map.of("type", "SKILL", "title", "Tools", "tags", List.of("Docker", "Git")), 201);
        call(post("/api/profile/reindex"), 200);
        String jobId = postJson("/api/jobs/analyze", Map.of("text", "Backend Developer at Nexa Fintech. "
                + "We build Spring Boot REST APIs on PostgreSQL. Kubernetes knowledge required. ".repeat(3)), 200)
                .get("id").asText();
        return postJson("/api/applications", Map.of("jobId", jobId), 201).get("id").asText();
    }

    private JsonNode waitForJob(String jobId, String status) throws Exception {
        JsonNode job = null;
        for (int i = 0; i < 100; i++) {
            job = call(get("/api/generation-jobs/" + jobId), 200);
            if (status.equals(job.get("status").asText())) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job did not reach " + status + ": " + job);
    }

    // ---------- tests ----------

    @Test
    void generatesValidatedCvAndLetterAsynchronously() throws Exception {
        String appId = fillProfileAndCreateApplication();

        JsonNode job = call(post("/api/applications/" + appId + "/generate"), 202);
        assertThat(job.get("status").asText()).isEqualTo("QUEUED");
        JsonNode done = waitForJob(job.get("id").asText(), "DONE");

        // The first CV mentioned Kubernetes (not in the profile) → one corrective retry with feedback.
        verify(llm, times(2)).writeCv(any(), any(), anyList(), eq(0.0));
        verify(llm).writeCv(any(), any(), argThat(f -> f != null && f.stream().anyMatch(m -> m.contains("Kubernetes"))),
                eq(0.0));

        JsonNode cv = call(get("/api/documents/" + done.get("cvDocumentId").asText()), 200);
        assertThat(cv.get("type").asText()).isEqualTo("CV");
        JsonNode content = cv.get("content");
        assertThat(content.get("header").get("fullName").asText()).isEqualTo("Sami Ben Ali");
        assertThat(content.get("experience").get(0).get("organization").asText()).isEqualTo("Vermeg");
        assertThat(content.get("experience").get(0).get("start").asText()).isEqualTo("2024-02-01");
        assertThat(content.get("experience").get(0).get("bullets").get(0).asText()).contains("PostgreSQL");
        assertThat(content.get("review")).isEmpty();
        // Kubernetes has no evidence → gap, whatever the judge said.
        assertThat(content.get("match").get("gaps").get(0).asText()).isEqualTo("Knowledge of Kubernetes");
        assertThat(cv.get("matchScore").asInt()).isBetween(1, 99);

        JsonNode letter = call(get("/api/documents/" + done.get("letterDocumentId").asText()), 200);
        assertThat(letter.get("content").get("signature").asText()).isEqualTo("Sami Ben Ali");
        assertThat(letter.get("content").get("company").asText()).isEqualTo("Nexa Fintech");

        JsonNode docs = call(get("/api/applications/" + appId + "/documents"), 200);
        assertThat(docs).hasSize(2);
        assertThat(call(get("/api/applications/" + appId + "/generation"), 200).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void onlyOneActiveGenerationPerApplication() throws Exception {
        String appId = fillProfileAndCreateApplication();
        CountDownLatch release = new CountDownLatch(1);
        when(llm.judgeMatch(any(), any())).thenAnswer(inv -> {
            release.await(10, TimeUnit.SECONDS);
            return List.of();
        });

        String jobId = call(post("/api/applications/" + appId + "/generate"), 202).get("id").asText();
        waitForJob(jobId, "RUNNING");
        call(post("/api/applications/" + appId + "/generate"), 409);

        release.countDown();
        waitForJob(jobId, "DONE");
    }

    @Test
    void editsKeepFactsAndAreRevalidated() throws Exception {
        String appId = fillProfileAndCreateApplication();
        String jobId = call(post("/api/applications/" + appId + "/generate"), 202).get("id").asText();
        String cvId = waitForJob(jobId, "DONE").get("cvDocumentId").asText();
        JsonNode content = call(get("/api/documents/" + cvId), 200).get("content");

        ObjectNode edited = content.deepCopy();
        edited.put("summary", "I scaled systems at Google to 10 million users.");
        ((ObjectNode) edited.get("experience").get(0)).put("organization", "Google");
        ((ObjectNode) edited.get("match")).put("score", 100);

        JsonNode saved = postJsonPut("/api/documents/" + cvId, Map.of("content", edited)).get("content");
        assertThat(saved.get("summary").asText()).startsWith("I scaled systems");
        assertThat(saved.get("experience").get(0).get("organization").asText()).isEqualTo("Vermeg");
        assertThat(saved.get("match").get("score").asInt()).isNotEqualTo(100);
        assertThat(saved.get("review").toString()).contains("Google").contains("10");
    }

    @Test
    void translatesIntoANewVersionKeepingFacts() throws Exception {
        String appId = fillProfileAndCreateApplication();
        String cvId = waitForJob(call(post("/api/applications/" + appId + "/generate"), 202).get("id").asText(), "DONE")
                .get("cvDocumentId").asText();
        when(llm.translate(anyList(), eq("fr"))).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> "FR " + t).toList();
        });

        JsonNode doc = postJson("/api/documents/" + cvId + "/translate", Map.of("language", "fr"), 200);
        assertThat(doc.get("id").asText()).isNotEqualTo(cvId);
        assertThat(doc.get("version").asInt()).isEqualTo(2);
        assertThat(doc.get("language").asText()).isEqualTo("fr");
        JsonNode content = doc.get("content");
        assertThat(content.get("summary").asText()).startsWith("FR ");
        assertThat(content.get("experience").get(0).get("title").asText()).isEqualTo("FR Software Engineering Intern");
        assertThat(content.get("experience").get(0).get("organization").asText()).isEqualTo("Vermeg");
        assertThat(content.get("skills").get(0).get("items").toString()).contains("\"Docker\"");
        assertThat(call(get("/api/documents/" + cvId), 200).get("language").asText()).isEqualTo("en"); // original kept

        postJson("/api/documents/" + doc.get("id").asText() + "/translate", Map.of("language", "fr"), 400);
        postJson("/api/documents/" + cvId + "/translate", Map.of("language", "de"), 400);
    }

    @Test
    void regeneratesOneSection() throws Exception {
        String appId = fillProfileAndCreateApplication();
        String cvId = waitForJob(call(post("/api/applications/" + appId + "/generate"), 202).get("id").asText(), "DONE")
                .get("cvDocumentId").asText();
        doReturn(new CvDraftOut("A fresh summary about Vermeg.",
                List.of(new RefBullets("E1", List.of("Changed bullet"))), List.of(), List.of()))
                .when(llm).writeCv(any(), any(), anyList(), eq(0.7));

        JsonNode doc = postJson("/api/documents/" + cvId + "/regenerate-section", Map.of("section", "summary"), 200);
        assertThat(doc.get("content").get("summary").asText()).isEqualTo("A fresh summary about Vermeg.");
        assertThat(doc.get("content").get("experience").get(0).get("bullets").get(0).asText())
                .isEqualTo("Built Spring Boot REST APIs on PostgreSQL"); // other sections untouched

        postJson("/api/documents/" + cvId + "/regenerate-section", Map.of("section", "header"), 400);
    }

    @Test
    void refusesEmptyProfilesAndOtherUsersData() throws Exception {
        String jobId = postJson("/api/jobs/analyze", Map.of("text", "Backend Developer at Nexa Fintech. ".repeat(6)), 200)
                .get("id").asText();
        String appId = postJson("/api/applications", Map.of("jobId", jobId), 201).get("id").asText();
        call(post("/api/applications/" + appId + "/generate"), 400); // no experience/education yet

        String other = register();
        mvc.perform(auth(get("/api/applications/" + appId), other)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/applications/" + appId + "/generate"), other)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"" + jobId + "\"}"), other)).andExpect(status().isNotFound());
    }

    private JsonNode postJsonPut(String url, Object body) throws Exception {
        return call(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), 200);
    }
}
