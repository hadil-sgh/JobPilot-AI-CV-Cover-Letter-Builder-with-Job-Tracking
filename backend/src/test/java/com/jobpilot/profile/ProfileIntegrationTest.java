package com.jobpilot.profile;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jobpilot.AbstractIntegrationTest;
import com.jobpilot.profile.cv.CvDraft;
import com.jobpilot.profile.cv.CvStructurer;
import com.jobpilot.profile.cv.TestFiles;

class ProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /** The LLM is replaced by a mock: tests never need Ollama. */
    @MockitoBean
    CvStructurer structurer;

    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        token = registerAndGetToken();
    }

    private String registerAndGetToken() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"p-" + UUID.randomUUID() + "@example.com\",\"password\":\"s3cret-pass\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String accessToken) {
        return req.header("Authorization", "Bearer " + accessToken);
    }

    private JsonNode addItem(String accessToken, String itemJson) throws Exception {
        String body = mvc.perform(auth(post("/api/profile/items"), accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void newUserGetsEmptyProfile() throws Exception {
        mvc.perform(auth(get("/api/profile"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.hasOriginalFile").value(false));
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void updatesHeaderFieldsAndName() throws Exception {
        mvc.perform(auth(put("/api/profile"), token).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Ada Lovelace","headline":"Backend Engineer","summary":"Java & Spring",
                         "phone":"+216 1","location":"Tunis",
                         "links":[{"label":"GitHub","url":"https://github.com/ada"}],
                         "languages":[{"name":"French","level":"Native"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/ada"))
                .andExpect(jsonPath("$.languages[0].level").value("Native"));
    }

    @Test
    void rejectsNonHttpLinks() throws Exception {
        mvc.perform(auth(put("/api/profile"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\":[{\"label\":\"x\",\"url\":\"javascript:alert(1)\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itemCrudAndReorder() throws Exception {
        JsonNode first = addItem(token, """
                {"type":"EXPERIENCE","title":"Intern","organization":"Acme","startDate":"2020-01-01",
                 "endDate":"2020-06-01","bullets":["Wrote tests"],"tags":[]}""");
        JsonNode second = addItem(token, "{\"type\":\"EXPERIENCE\",\"title\":\"Engineer\",\"organization\":\"Globex\"}");
        String firstId = first.get("id").asText();
        String secondId = second.get("id").asText();

        mvc.perform(auth(put("/api/profile/items/" + firstId), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPERIENCE\",\"title\":\"Software Intern\",\"organization\":\"Acme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Software Intern"));

        mvc.perform(auth(put("/api/profile/items/order"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPERIENCE\",\"ids\":[\"" + secondId + "\",\"" + firstId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Engineer"))
                .andExpect(jsonPath("$.items[1].title").value("Software Intern"));

        mvc.perform(auth(delete("/api/profile/items/" + secondId), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/profile"), token))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(firstId));
    }

    @Test
    void rejectsInvalidItems() throws Exception {
        mvc.perform(auth(post("/api/profile/items"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPERIENCE\",\"startDate\":\"2022-01-01\",\"endDate\":\"2021-01-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/profile/items"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"HOBBY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotTouchAnotherUsersItems() throws Exception {
        String itemId = addItem(token, "{\"type\":\"SKILL\",\"title\":\"Languages\",\"tags\":[\"Java\"]}").get("id").asText();
        String otherToken = registerAndGetToken();

        mvc.perform(auth(put("/api/profile/items/" + itemId), otherToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SKILL\",\"title\":\"hacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/profile/items/" + itemId), otherToken)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/profile"), token)).andExpect(jsonPath("$.items[0].title").value("Languages"));
    }

    @Test
    void importsPdfThroughTheStructurer() throws Exception {
        when(structurer.structure(anyString())).thenReturn(new CvDraft("Grace Hopper", "Compiler Engineer", "Summary",
                new CvDraft.Contact("g@example.com", "+1 555", "NYC", List.of(new CvDraft.Link("Site", "https://grace.dev"))),
                List.of(new CvDraft.Experience("Engineer", "UNIVAC", null, "2019", "2023", null, List.of("Wrote A-0"))),
                List.of(), List.of(), List.of(new CvDraft.SkillGroup("Languages", List.of("COBOL"))),
                List.of(), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                TestFiles.pdf("Grace Hopper", "Compiler Engineer at UNIVAC 2019 - 2023", "Wrote the A-0 compiler"));

        mvc.perform(auth(multipart("/api/profile/import").file(file), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Grace Hopper"))
                .andExpect(jsonPath("$.headline").value("Compiler Engineer"))
                .andExpect(jsonPath("$.hasOriginalFile").value(true))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("EXPERIENCE"))
                .andExpect(jsonPath("$.items[0].startDate").value("2019-01-01"));
    }

    @Test
    void importRejectsUnsupportedFilesBeforeCallingTheLlm() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "just some plain text pretending to be a PDF file".getBytes(StandardCharsets.UTF_8));

        mvc.perform(auth(multipart("/api/profile/import").file(file), token))
                .andExpect(status().isUnsupportedMediaType());
        verify(structurer, never()).structure(anyString());
    }
}
