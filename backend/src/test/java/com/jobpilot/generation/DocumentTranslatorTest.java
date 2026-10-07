package com.jobpilot.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.llm.GenerationLlm;
import com.jobpilot.profile.ProfileLanguage;

class DocumentTranslatorTest {

    private final GenerationLlm llm = mock(GenerationLlm.class);
    private final DocumentTranslator translator = new DocumentTranslator(llm);

    private void prefixWithFr() {
        when(llm.translate(anyList(), eq("fr"))).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> "FR " + t).toList();
        });
    }

    private static CvContent cv() {
        CvContent.Entry job = new CvContent.Entry("E1", UUID.randomUUID(), "Software Engineer", "Vermeg",
                LocalDate.of(2024, 1, 1), null, null, List.of("Built REST APIs"), List.of("Java"));
        return new CvContent("en", new CvContent.Header("Sami Ben Ali", "Backend developer", "s@x.io", "+216", "Tunis",
                List.of()), "Backend developer who builds APIs.", List.of(job), List.of(), List.of(),
                List.of(new CvContent.SkillGroup("Tools", List.of("Docker", "Git"))),
                List.of(new ProfileLanguage("French", "Native")), List.of(), null, List.of());
    }

    @Test
    void translatesWordingAndKeepsFacts() {
        prefixWithFr();
        CvContent out = translator.translateCv(cv(), "fr");

        assertThat(out.language()).isEqualTo("fr");
        assertThat(out.summary()).isEqualTo("FR Backend developer who builds APIs.");
        assertThat(out.header().headline()).isEqualTo("FR Backend developer");
        assertThat(out.header().fullName()).isEqualTo("Sami Ben Ali");
        CvContent.Entry e = out.experience().get(0);
        assertThat(e.title()).isEqualTo("FR Software Engineer");
        assertThat(e.organization()).isEqualTo("Vermeg");
        assertThat(e.start()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(e.bullets()).containsExactly("FR Built REST APIs");
        assertThat(e.tags()).containsExactly("Java");
        assertThat(out.skills().get(0).group()).isEqualTo("FR Tools");
        assertThat(out.skills().get(0).items()).containsExactly("Docker", "Git");
        assertThat(out.languages().get(0)).isEqualTo(new ProfileLanguage("FR French", "FR Native"));
    }

    @Test
    void skipsTextsAlreadyInTheTargetLanguageAndKeepsOriginalsOnMissingAnswers() {
        when(llm.translate(anyList(), eq("fr"))).thenReturn(List.of()); // model answered nothing usable
        LetterContent letter = new LetterContent("en", "Nexa", "Backend Developer", "Dear Hiring Manager,",
                List.of("Je suis ravi de postuler pour ce poste chez vous avec mon expérience."), "Kind regards,",
                "Sami", List.of());

        LetterContent out = translator.translateLetter(letter, "fr");

        assertThat(out.language()).isEqualTo("fr");
        assertThat(out.greeting()).isEqualTo("Dear Hiring Manager,");
        assertThat(out.company()).isEqualTo("Nexa");
        verify(llm).translate(eq(List.of("Backend Developer", "Dear Hiring Manager,", "Kind regards,")), eq("fr"));
    }

    @Test
    void sendsLongDocumentsInBatchesAndNothingWhenThereIsNothingToTranslate() {
        prefixWithFr();
        List<String> bullets = IntStream.range(0, 30).mapToObj(i -> "Bullet number " + i).toList();
        CvContent many = cv().withExperience(List.of(new CvContent.Entry("E1", null, null, "Vermeg", null, null, null,
                bullets, List.of())));
        CvContent out = translator.translateCv(many, "fr");
        assertThat(out.experience().get(0).bullets()).hasSize(30).allMatch(b -> b.startsWith("FR "));

        GenerationLlm idle = mock(GenerationLlm.class);
        CvContent empty = new CvContent("en", null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of());
        new DocumentTranslator(idle).translateCv(empty, "fr");
        verify(idle, never()).translate(anyList(), eq("fr"));
    }
}
