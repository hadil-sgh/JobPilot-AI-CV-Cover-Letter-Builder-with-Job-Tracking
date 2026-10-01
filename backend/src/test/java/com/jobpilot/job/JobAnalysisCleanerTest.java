package com.jobpilot.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class JobAnalysisCleanerTest {

    @Test
    void normalisesLanguageAndSeniority() {
        // Short/ambiguous text: the model's answer decides.
        assertThat(JobAnalysisCleaner.language("English", "")).isEqualTo("en");
        assertThat(JobAnalysisCleaner.language("fr-FR", "")).isEqualTo("fr");
        assertThat(JobAnalysisCleaner.language("Français", "")).isEqualTo("fr");
        assertThat(JobAnalysisCleaner.language("??", "")).isEqualTo("en");
        // Clear text majority beats the model (real llama3 mistake: English offer requiring "Fluent French").
        String english = "We are looking for a developer to join our team. You have experience with Java and the "
                + "skills to build our APIs. Fluent French, working English.";
        assertThat(JobAnalysisCleaner.language("fr", english)).isEqualTo("en");
        String french = "Nous recherchons un développeur pour notre équipe. Vous avez de l'expérience avec Java et "
                + "les compétences pour le poste. Anglais courant.";
        assertThat(JobAnalysisCleaner.language("en", french)).isEqualTo("fr");

        assertThat(JobAnalysisCleaner.seniority("Senior (5+ years)")).isEqualTo("senior");
        assertThat(JobAnalysisCleaner.seniority("Stagiaire")).isEqualTo("intern");
        assertThat(JobAnalysisCleaner.seniority("confirmé")).isEqualTo("mid");
        assertThat(JobAnalysisCleaner.seniority("whatever")).isNull();
    }

    @Test
    void cleansAndCapsLists() {
        List<String> many = Collections.nCopies(3, "Java");
        JobAnalysis raw = new JobAnalysis("  Backend Dev ", null, "en", "junior",
                List.of("3 years Java", " ", "3 YEARS JAVA", "Docker\u0000"), null, many, null, "formal");

        JobAnalysis a = JobAnalysisCleaner.clean(raw, "");

        assertThat(a.title()).isEqualTo("Backend Dev");
        assertThat(a.requirements()).containsExactly("3 years Java", "Docker");
        assertThat(a.keywords()).containsExactly("Java");
        assertThat(a.niceToHave()).isEmpty();
        assertThat(a.responsibilities()).isEmpty();
    }
}
