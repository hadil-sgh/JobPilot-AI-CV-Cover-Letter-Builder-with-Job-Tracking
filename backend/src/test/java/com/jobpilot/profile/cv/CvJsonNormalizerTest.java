package com.jobpilot.profile.cv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.jobpilot.common.llm.LlmJson;
import org.junit.jupiter.api.Test;

class CvJsonNormalizerTest {

    private final ObjectMapper json = LlmJson.lenientMapper(new ObjectMapper());

    private CvDraft parse(String raw) throws Exception {
        return json.treeToValue(CvJsonNormalizer.normalize(json.readTree(raw)), CvDraft.class);
    }

    @Test
    void repairsTheShapesSmallModelsGetWrong() throws Exception {
        // Real failure seen with llama3 8B: a project given as a plain string.
        CvDraft d = parse("""
                {"projects": ["JobTrack", {"name": "Site", "tags": "Angular"}],
                 "experience": {"title": "Intern", "org": "Vermeg", "bullets": "Built APIs"},
                 "skills": ["Java", {"group": "Tools", "items": ["Docker"]}],
                 "languages": ["French"],
                 "certifications": "OCP Java",
                 "contact": {"links": {"label": "GitHub", "url": "https://github.com/x"}}}""");

        assertThat(d.projects()).extracting(CvDraft.Project::name).containsExactly("JobTrack", "Site");
        assertThat(d.projects().get(1).tags()).containsExactly("Angular");
        assertThat(d.experience()).hasSize(1);
        assertThat(d.experience().get(0).bullets()).containsExactly("Built APIs");
        assertThat(d.skills().get(0).items()).containsExactly("Java");
        assertThat(d.skills().get(1).group()).isEqualTo("Tools");
        assertThat(d.languages().get(0).name()).isEqualTo("French");
        assertThat(d.certifications().get(0).name()).isEqualTo("OCP Java");
        assertThat(d.contact().links()).hasSize(1);
    }

    @Test
    void flattensObjectsWhereTextIsExpected() throws Exception {
        // Real failure seen with llama3 8B: an object where a string field was expected.
        CvDraft d = parse("""
                {"summary": {"text": "Backend developer"},
                 "contact": {"location": {"city": "Tunis", "country": "Tunisia"}},
                 "projects": [{"name": "JobTrack", "tags": [{"name": "Angular"}, "Docker"]}]}""");

        assertThat(d.summary()).isEqualTo("Backend developer");
        assertThat(d.contact().location()).isEqualTo("Tunis, Tunisia");
        assertThat(d.projects().get(0).tags()).containsExactly("Angular", "Docker");
    }

    @Test
    void leavesWellFormedOutputUntouchedAndDropsJunkEntries() throws Exception {
        CvDraft d = parse("""
                {"experience": [{"title": "Dev", "org": "Acme", "bullets": ["a", "b"]}, 42, null],
                 "contact": "not an object", "education": null}""");

        assertThat(d.experience()).hasSize(1);
        assertThat(d.experience().get(0).bullets()).containsExactly("a", "b");
        assertThat(d.contact()).isNull();
        assertThat(d.education()).isNull();
    }
}
