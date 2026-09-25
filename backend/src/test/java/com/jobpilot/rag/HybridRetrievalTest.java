package com.jobpilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jobpilot.rag.ProfileChunkRepository.Hit;
import com.jobpilot.rag.RetrievalService.Evidence;

/**
 * Ranking regression test with REAL nomic-embed-text similarities measured on 2026-09-25 against
 * the sample profile (docs/DECISIONS.md, Phase 3). Pure cosine ≥ 0.55 got all three cases wrong.
 */
class HybridRetrievalTest {

    private final RetrievalService retrieval = new RetrievalService(null, null, 4, 0.35, 0.45, 0.70);

    private static final String SKILLS = "Skills (Frameworks): Spring Boot, Angular";
    private static final String SUMMARY = "Profile summary: Junior Full-Stack Developer.";
    private static final String EDUCATION = "Education: Engineering Degree in Software Engineering at ESPRIT (2020-01 to 2025-01)";
    private static final String PROJECT = "Project: JobTrack\nAngular 17, Spring Boot, Docker: job application tracker";

    private static Hit hit(String content, double similarity) {
        return new Hit(UUID.randomUUID(), null, content, Map.of("type", content.split(":")[0]), similarity);
    }

    private List<Evidence> rank(String requirement, double skills, double summary, double education, double project) {
        return retrieval.rank(requirement, List.of(hit(SKILLS, skills), hit(SUMMARY, summary),
                hit(EDUCATION, education), hit(PROJECT, project)));
    }

    @Test
    void angularIsFoundInTheSkillList() {
        // Pure cosine: best was the generic summary (0.513) and nothing passed 0.55.
        List<Evidence> ev = rank("Angular or another frontend framework", 0.505, 0.513, 0.450, 0.487);
        assertThat(ev).extracting(e -> e.hit().content()).containsExactly(SKILLS, PROJECT);
        assertThat(ev.get(0).matchedTerms()).containsExactly("angular", "framework");
    }

    @Test
    void kubernetesHasNoEvidence() {
        // Pure cosine: the summary "matched" at 0.641 although the profile never mentions Kubernetes.
        assertThat(rank("Knowledge of Kubernetes", 0.630, 0.641, 0.544, 0.528)).isEmpty();
    }

    @Test
    void degreeRequirementPointsToEducation() {
        // Pure cosine: the project ranked first (0.552) and the education chunk was below threshold.
        List<Evidence> ev = rank("Engineering degree in computer science or equivalent", 0.534, 0.522, 0.491, 0.552);
        assertThat(ev).extracting(e -> e.hit().content()).containsExactly(EDUCATION);
        assertThat(ev.get(0).matchedTerms()).containsExactly("engineering", "degree");
    }

    @Test
    void veryHighSimilarityCountsEvenWithoutSharedWords() {
        List<Evidence> ev = retrieval.rank("Build scalable backend services",
                List.of(hit("Experience: API developer at Acme", 0.74)));
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).matchedTerms()).isEmpty();
    }

    @Test
    void termsIgnoreGenericJobWordsAndFoldPlurals() {
        assertThat(LexicalMatcher.terms("3+ years of experience with relational databases and CI/CD, Node.js, C#"))
                .containsExactly("3+", "relational", "database", "ci/cd", "node.js", "c#");
        assertThat(LexicalMatcher.terms("Bonne maîtrise de Java et des bases de données"))
                .containsExactly("java", "base", "donnee");
    }
}
