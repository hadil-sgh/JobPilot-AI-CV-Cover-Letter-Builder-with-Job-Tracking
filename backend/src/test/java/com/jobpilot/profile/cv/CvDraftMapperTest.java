package com.jobpilot.profile.cv;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileItem;

class CvDraftMapperTest {

    private final CvDraftMapper mapper = new CvDraftMapper();

    /** A realistic prompt-A answer, including junk the mapper must clean up. */
    private static final String LLM_JSON = """
            {
              "fullName": "  Ada   Lovelace ",
              "headline": "Software Engineer",
              "summary": "Backend developer.\\u0007",
              "contact": {"email": "ada@example.com", "phone": "+216 12 345 678", "location": "Tunis",
                          "links": [{"label": "GitHub", "url": "github.com/ada"},
                                    {"label": "Evil", "url": "javascript:alert(1)"},
                                    {"label": "Site", "url": "https://ada.dev"}]},
              "experience": [
                {"title": "Backend Engineer", "org": "Analytical Engines", "start": "Mar 2021", "end": "Present",
                 "bullets": ["Built a Spring Boot API", "Built a Spring Boot API", "  ", "Cut latency by 35%"]},
                {"title": null, "org": null, "bullets": []}
              ],
              "education": [{"degree": "MSc Computer Science", "school": "ESPRIT", "start": "2019", "end": "2021"}],
              "projects": [{"name": "JobPilot", "description": "CV builder", "bullets": [], "tags": ["Angular", "Spring"]}],
              "skills": [{"group": null, "items": ["Java", "SQL", "java "]}, {"group": "Empty", "items": []}],
              "languages": [{"name": "French", "level": "Native"}, {"name": "", "level": "B2"}],
              "certifications": [{"name": "OCP Java 17", "issuer": "Oracle", "date": "2023-06"}],
              "unexpectedField": "ignored"
            }
            """;

    private Profile applyExample() throws Exception {
        CvDraft draft = new ObjectMapper().readValue(LLM_JSON, CvDraft.class);
        Profile profile = new Profile(UUID.randomUUID());
        assertThat(mapper.apply(draft, profile)).isEqualTo("Ada Lovelace");
        return profile;
    }

    private static List<ProfileItem> ofType(Profile p, ItemType type) {
        return p.getItems().stream().filter(i -> i.getType() == type).toList();
    }

    @Test
    void mapsHeaderAndContactSafely() throws Exception {
        Profile p = applyExample();
        assertThat(p.getHeadline()).isEqualTo("Software Engineer");
        assertThat(p.getSummary()).isEqualTo("Backend developer.");
        assertThat(p.getPhone()).isEqualTo("+216 12 345 678");
        // bare domain gets https://, javascript: URL is dropped
        assertThat(p.getLinks()).extracting(l -> l.url()).containsExactly("https://github.com/ada", "https://ada.dev");
        assertThat(p.getLanguages()).extracting(l -> l.name()).containsExactly("French");
    }

    @Test
    void mapsItemsWithDatesDedupedBulletsAndSkillGroups() throws Exception {
        Profile p = applyExample();

        List<ProfileItem> exp = ofType(p, ItemType.EXPERIENCE);
        assertThat(exp).hasSize(1); // the all-null entry is dropped
        assertThat(exp.get(0).getStartDate()).isEqualTo(LocalDate.of(2021, 3, 1));
        assertThat(exp.get(0).getEndDate()).isNull(); // "Present"
        assertThat(exp.get(0).getBullets()).containsExactly("Built a Spring Boot API", "Cut latency by 35%");

        ProfileItem edu = ofType(p, ItemType.EDUCATION).get(0);
        assertThat(edu.getTitle()).isEqualTo("MSc Computer Science");
        assertThat(edu.getOrganization()).isEqualTo("ESPRIT");

        List<ProfileItem> skills = ofType(p, ItemType.SKILL);
        assertThat(skills).hasSize(1); // empty group dropped
        assertThat(skills.get(0).getTitle()).isEqualTo("Skills");
        assertThat(skills.get(0).getTags()).containsExactly("Java", "SQL", "java");

        assertThat(ofType(p, ItemType.PROJECT).get(0).getTags()).containsExactly("Angular", "Spring");
        assertThat(ofType(p, ItemType.CERTIFICATION).get(0).getStartDate()).isEqualTo(LocalDate.of(2023, 6, 1));
    }

    @Test
    void replacesExistingItemsAndCapsLists() {
        Profile p = new Profile(UUID.randomUUID());
        p.addItem(new ProfileItem(ItemType.PROJECT));
        List<String> manyBullets = new ArrayList<>(Collections.nCopies(1, "x".repeat(900)));
        for (int i = 0; i < 40; i++) {
            manyBullets.add("bullet " + i);
        }
        CvDraft draft = new CvDraft(null, null, null, null,
                List.of(new CvDraft.Experience("Dev", "Acme", null, null, null, null, manyBullets)),
                null, null, null, null, null);

        mapper.apply(draft, p);

        assertThat(p.getItems()).hasSize(1);
        ProfileItem item = p.getItems().get(0);
        assertThat(item.getBullets()).hasSize(CvDraftMapper.MAX_BULLETS);
        assertThat(item.getBullets().get(0)).hasSize(500);
    }

    @Test
    void cleanRemovesControlCharsAndTreatsNullStringAsMissing() {
        assertThat(CvDraftMapper.clean("a\u0000b  \t c", 100)).isEqualTo("a b c");
        assertThat(CvDraftMapper.clean("null", 100)).isNull();
        assertThat(CvDraftMapper.clean("   ", 100)).isNull();
        assertThat(CvDraftMapper.clean("abcdef", 3)).isEqualTo("abc");
    }
}
