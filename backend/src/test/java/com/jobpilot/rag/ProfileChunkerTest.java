package com.jobpilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.Profile;
import com.jobpilot.profile.ProfileItem;
import com.jobpilot.profile.ProfileLanguage;

class ProfileChunkerTest {

    private final ProfileChunker chunker = new ProfileChunker();

    private static ProfileItem item(ItemType type, String title, String org) {
        ProfileItem i = new ProfileItem(type);
        i.setTitle(title);
        i.setOrganization(org);
        return i;
    }

    @Test
    void oneChunkPerItemPlusSummaryAndLanguages() {
        Profile p = new Profile(UUID.randomUUID());
        p.setHeadline("Backend developer");
        p.setSummary("Java and Spring");
        p.setLanguages(List.of(new ProfileLanguage("French", "C1"), new ProfileLanguage("Arabic", null)));

        ProfileItem exp = item(ItemType.EXPERIENCE, "Intern", "Vermeg");
        exp.setStartDate(LocalDate.of(2024, 2, 1));
        exp.setBullets(List.of("Built REST APIs", "Cut latency by 40%"));
        p.addItem(exp);

        ProfileItem skills = item(ItemType.SKILL, "Languages", null);
        skills.setTags(List.of("Java", "SQL"));
        p.addItem(skills);
        p.addItem(item(ItemType.SKILL, "Empty group", null)); // no tags → no chunk

        ProfileItem project = item(ItemType.PROJECT, "JobTrack", null);
        project.setTags(List.of("Angular", "Docker"));
        p.addItem(project);

        List<Chunk> chunks = chunker.chunk(p);

        assertThat(chunks).extracting(c -> c.metadata().get("type"))
                .containsExactly("SUMMARY", "LANGUAGES", "EXPERIENCE", "PROJECT", "SKILL");
        assertThat(chunks.get(0).content()).isEqualTo("Profile summary: Backend developer. Java and Spring.");
        assertThat(chunks.get(1).content()).isEqualTo("Spoken languages: French (C1), Arabic");
        assertThat(chunks.get(2).content())
                .isEqualTo("Experience: Intern at Vermeg (2024-02 to present)\n- Built REST APIs\n- Cut latency by 40%");
        assertThat(chunks.get(2).metadata()).containsEntry("org", "Vermeg").containsEntry("start", "2024-02-01");
        assertThat(chunks.get(3).content()).isEqualTo("Project: JobTrack\nTechnologies: Angular, Docker");
        assertThat(chunks.get(4).content()).isEqualTo("Skills (Languages): Java, SQL");
    }

    @Test
    void emptyProfileHasNoChunksAndHashesAreStable() {
        assertThat(chunker.chunk(new Profile(UUID.randomUUID()))).isEmpty();
        Chunk a = new Chunk(null, "same text", java.util.Map.of());
        Chunk b = new Chunk(UUID.randomUUID(), "same text", java.util.Map.of("x", 1));
        assertThat(a.contentHash()).isEqualTo(b.contentHash()).hasSize(64);
    }

    @Test
    void vectorLiteralRoundTrips() {
        float[] v = {0.5f, -1.25f, 3f};
        assertThat(ProfileChunkRepository.toVector(v)).isEqualTo("[0.5,-1.25,3.0]");
        assertThat(ProfileChunkRepository.parseVector("[0.5,-1.25,3]")).containsExactly(0.5f, -1.25f, 3f);
    }
}
