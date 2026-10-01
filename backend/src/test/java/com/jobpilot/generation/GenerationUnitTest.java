package com.jobpilot.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.llm.GenerationModels.CvDraftOut;
import com.jobpilot.generation.llm.GenerationModels.LetterDraftOut;
import com.jobpilot.generation.llm.GenerationModels.RefBullets;
import com.jobpilot.generation.llm.GenerationModels.SkillGroupOut;
import com.jobpilot.generation.llm.GenerationModels.Verdict;
import com.jobpilot.job.JobAnalysis;
import com.jobpilot.profile.ItemType;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.rag.ProfileChunkRepository.Hit;
import com.jobpilot.rag.RetrievalService.Evidence;

class GenerationUnitTest {

    static final UUID VERMEG = UUID.randomUUID();
    static final UUID FREELANCE = UUID.randomUUID();
    static final UUID ESPRIT = UUID.randomUUID();
    static final UUID JOBTRACK = UUID.randomUUID();
    static final UUID TOOLS = UUID.randomUUID();

    static ProfileSnapshot profile() {
        List<ProfileSnapshot.Item> items = List.of(
                new ProfileSnapshot.Item(VERMEG, ItemType.EXPERIENCE, "Software Engineering Intern", "Vermeg",
                        LocalDate.of(2024, 2, 1), LocalDate.of(2024, 8, 1), null,
                        List.of("Built REST APIs with Spring Boot", "Cut report time by 40%"), List.of(), 0),
                new ProfileSnapshot.Item(FREELANCE, ItemType.EXPERIENCE, "Freelance Web Developer", null,
                        LocalDate.of(2022, 9, 1), LocalDate.of(2024, 1, 1), null,
                        List.of("Delivered 6 Angular websites"), List.of(), 1),
                new ProfileSnapshot.Item(ESPRIT, ItemType.EDUCATION, "Engineering Degree", "ESPRIT",
                        LocalDate.of(2020, 9, 1), LocalDate.of(2025, 6, 1), null, List.of(), List.of(), 0),
                new ProfileSnapshot.Item(JOBTRACK, ItemType.PROJECT, "JobTrack", null, null, null,
                        "Job tracker", List.of("Kanban board in Angular"), List.of("Angular", "Docker"), 0),
                new ProfileSnapshot.Item(TOOLS, ItemType.SKILL, "Tools", null, null, null, null, List.of(),
                        List.of("Docker", "Git", "GitHub Actions"), 0));
        Map<UUID, String> chunks = Map.of(
                VERMEG, "Experience: Software Engineering Intern at Vermeg\n- Built REST APIs with Spring Boot",
                FREELANCE, "Experience: Freelance Web Developer\n- Delivered 6 Angular websites",
                ESPRIT, "Education: Engineering Degree at ESPRIT",
                JOBTRACK, "Project: JobTrack\nTechnologies: Angular, Docker",
                TOOLS, "Skills (Tools): Docker, Git, GitHub Actions");
        return new ProfileSnapshot(UUID.randomUUID(), "Sami Ben Ali", "sami@example.com", "Junior developer", null,
                "+216 1", "Tunis", List.of(), List.of(new ProfileLanguage("French", "C1")), items, chunks,
                String.join("\n", chunks.values()));
    }

    static GenerationContext ctx() {
        JobAnalysis a = new JobAnalysis("Backend Developer", "Nexa Fintech", "en", "junior",
                List.of("Spring Boot REST APIs", "Kubernetes"), List.of("Angular"), List.of("Spring Boot"),
                List.of(), "friendly");
        return new GenerationContext(UUID.randomUUID(), UUID.randomUUID(), "Nexa Fintech", "Backend Developer", a, "en",
                profile());
    }

    static Evidence ev(UUID itemId, double score) {
        return new Evidence(new Hit(UUID.randomUUID(), itemId, "x", Map.of(), score), score, List.of());
    }

    static EvidencePack pack() {
        return new EvidencePackBuilder().build(profile(), ctx().analysis(), req -> switch (req) {
            case "Spring Boot REST APIs" -> List.of(ev(VERMEG, 0.9));
            case "Angular" -> List.of(ev(JOBTRACK, 0.74), ev(FREELANCE, 0.6));
            default -> List.of();
        });
    }

    @Test
    void evidencePackAssignsRefsAndLinksRequirements() {
        EvidencePack p = pack();
        assertThat(p.items()).extracting(EvidencePack.Item::ref).containsExactly("E1", "E2", "D1", "P1", "S1");
        assertThat(p.byRef().get("P1").supports()).containsExactly("Angular");
        assertThat(p.requirements()).extracting(EvidencePack.Requirement::refs)
                .containsExactly(List.of("E1"), List.of(), List.of("P1", "E2"));
        assertThat(p.render()).contains("[E1] Experience: Software Engineering Intern at Vermeg")
                .contains("(relevant to: Spring Boot REST APIs)");
    }

    @Test
    void matchScoreWeighsMustHavesAndListsGaps() {
        CvContent.Match m = MatchScorer.score(pack().requirements(),
                List.of(new Verdict(1, "yes"), new Verdict(2, "yes"), new Verdict(3, "Partial")));
        // Kubernetes has no evidence: forced to "no" even though the LLM said yes.
        assertThat(m.requirements()).extracting(CvContent.RequirementMatch::verdict).containsExactly("yes", "no", "partial");
        assertThat(m.gaps()).containsExactly("Kubernetes");
        // (2*(0.6+0.4*0.818) + 2*0 + 1*(0.3+0.4*0.527)) / 5 ≈ 47
        assertThat(m.score()).isBetween(45, 49);
        assertThat(MatchScorer.score(List.of(), List.of()).score()).isZero();
    }

    @Test
    void cvFactsComeFromTheProfileNotTheModel() {
        CvDraftOut draft = new CvDraftOut("Backend developer.",
                List.of(new RefBullets("[E2]", List.of("Shipped 6 Angular sites")), new RefBullets("E9", List.of("ghost"))),
                List.of(new RefBullets("P1", List.of())),
                List.of(new SkillGroupOut("DevOps", List.of("docker", "Kubernetes", "Git"))));

        CvContent cv = new ContentAssembler().assembleCv(ctx(), pack(), draft, null);

        // LLM order kept, forgotten experience appended, facts copied from the profile.
        assertThat(cv.experience()).extracting(CvContent.Entry::ref).containsExactly("E2", "E1");
        assertThat(cv.experience().get(1).organization()).isEqualTo("Vermeg");
        assertThat(cv.experience().get(1).start()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(cv.experience().get(1).bullets()).containsExactly("Built REST APIs with Spring Boot", "Cut report time by 40%");
        assertThat(cv.experience().get(0).bullets()).containsExactly("Shipped 6 Angular sites");
        // Unknown ref reported; project without bullets keeps its own.
        assertThat(cv.review()).anyMatch(f -> f.message().contains("E9"));
        assertThat(cv.projects().get(0).bullets()).containsExactly("Kanban board in Angular");
        // Skills: profile spelling, invented skill dropped, forgotten ones kept.
        assertThat(cv.skills()).extracting(CvContent.SkillGroup::items)
                .containsExactly(List.of("Docker", "Git"), List.of("GitHub Actions"));
        assertThat(cv.education()).extracting(CvContent.Entry::organization).containsExactly("ESPRIT");
        assertThat(cv.header().fullName()).isEqualTo("Sami Ben Ali");
    }

    @Test
    void letterGetsDefaultsAndSignature() {
        var letter = new ContentAssembler().assembleLetter(ctx(), new LetterDraftOut(null, List.of("One.", " ", "Two."), null));
        assertThat(letter.greeting()).isEqualTo("Dear Hiring Manager,");
        assertThat(letter.paragraphs()).containsExactly("One.", "Two.");
        assertThat(letter.signature()).isEqualTo("Sami Ben Ali");
    }

    @Test
    void userEditsCannotChangeFacts() {
        CvContent stored = new ContentAssembler().assembleCv(ctx(), pack(), new CvDraftOut("Old.", List.of(), List.of(),
                List.of()), null);
        CvContent.Entry hacked = new CvContent.Entry("E1", VERMEG, "CTO", "Google", null, null, null,
                List.of("New bullet"), List.of());
        CvContent edited = stored.withSummary("New summary").withExperience(List.of(hacked)).withProjects(List.of());

        CvContent merged = DocumentEdits.applyUserEdits(stored, edited);

        assertThat(merged.summary()).isEqualTo("New summary");
        assertThat(merged.experience()).extracting(CvContent.Entry::ref).containsExactly("E1", "E2");
        assertThat(merged.experience().get(0).title()).isEqualTo("Software Engineering Intern");
        assertThat(merged.experience().get(0).organization()).isEqualTo("Vermeg");
        assertThat(merged.experience().get(0).bullets()).containsExactly("New bullet");
        assertThat(merged.projects()).isEmpty(); // projects may be removed
    }

    @Test
    void sectionMergeOnlyTouchesTheRequestedSection() {
        CvContent stored = new ContentAssembler().assembleCv(ctx(), pack(), new CvDraftOut("Old.", List.of(), List.of(),
                List.of()), null);
        CvContent fresh = new ContentAssembler().assembleCv(ctx(), pack(), new CvDraftOut("Fresh.",
                List.of(new RefBullets("E1", List.of("Rewritten"))), List.of(), List.of()), null);

        CvContent merged = DocumentEdits.mergeSection(stored, fresh, "experience:E1");
        assertThat(merged.summary()).isEqualTo("Old.");
        assertThat(merged.experience().stream().filter(e -> e.ref().equals("E1")).findFirst().orElseThrow().bullets())
                .containsExactly("Rewritten");
        assertThat(DocumentEdits.isCvSection("experience:E1")).isTrue();
        assertThat(DocumentEdits.isCvSection("header")).isFalse();
        assertThat(ContentAssembler.normalizeRef(" [e-3] ")).isEqualTo("E3");
    }
}
