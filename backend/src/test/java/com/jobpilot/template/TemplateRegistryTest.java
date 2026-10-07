package com.jobpilot.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.DocumentModels;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.profile.ProfileLanguage;
import com.jobpilot.profile.ProfileLink;

class TemplateRegistryTest {

    private final ClasspathTemplateRegistry registry = new ClasspathTemplateRegistry(new ObjectMapper());

    TemplateRegistryTest() throws Exception {
    }

    static CvContent cv(String summary) {
        CvContent.Header header = new CvContent.Header("Sami Ben Ali", "Backend developer", "sami@example.com",
                "+216 20 123 456", "Tunis", List.of(new ProfileLink("GitHub", "https://github.com/sami_ba#x")));
        CvContent.Entry exp = new CvContent.Entry("E1", UUID.randomUUID(), "Intern", "R&D Lab", LocalDate.of(2024, 2, 1),
                null, null, List.of("Cut costs by 40% using C# & SQL", "\\input{/etc/passwd}"), List.of());
        return new CvContent("en", header, summary, List.of(exp), List.of(), List.of(),
                List.of(new CvContent.SkillGroup("Tools", List.of("Docker", "Git"))),
                List.of(new ProfileLanguage("French", "C1")), List.of(), null, List.of());
    }

    @Test
    void discoversManifestAndAssets() {
        TemplateManifest m = registry.get("ats-classic");
        assertThat(m.atsSafe()).isTrue();
        assertThat(m.options()).containsKeys("fontSize", "accentColor", "density", "sectionOrder");
        assertThat(registry.assets("ats-classic")).containsKey("jobpilot-classic.sty");
        assertThatThrownBy(() -> registry.get("nope")).isInstanceOf(ApiException.class);
    }

    @Test
    void resolvesAndValidatesOptions() {
        Map<String, Object> defaults = registry.resolveOptions("ats-classic", null);
        assertThat(defaults).containsEntry("fontSize", "10pt").containsEntry("accentColor", "#1F3A5F");

        Map<String, Object> custom = registry.resolveOptions("ats-classic",
                Map.of("accentColor", "#ff3e1d", "sectionOrder", List.of("skills", "experience", "hacked", "skills")));
        assertThat(custom.get("accentColor")).isEqualTo("#FF3E1D");
        // Unknown and duplicate sections dropped, missing ones appended.
        @SuppressWarnings("unchecked")
        List<String> order = (List<String>) custom.get("sectionOrder");
        assertThat(order).startsWith("skills", "experience").doesNotContain("hacked").hasSize(7);

        assertThatThrownBy(() -> registry.resolveOptions("ats-classic", Map.of("fontSize", "72pt")))
                .hasMessageContaining("fontSize");
        assertThatThrownBy(() -> registry.resolveOptions("ats-classic", Map.of("accentColor", "red}\\evil")))
                .hasMessageContaining("accentColor");
    }

    @Test
    void cvTemplateEscapesEveryValueAndFollowsSectionOrder() {
        Map<String, Object> options = registry.resolveOptions("ats-classic",
                Map.of("sectionOrder", List.of("skills", "summary", "experience")));
        @SuppressWarnings("unchecked")
        Map<String, Object> model = DocumentModels.cv(cv("100% {focused} on_backend"), (List<String>) options.get("sectionOrder"));

        String tex = registry.render("ats-classic", "cv.tex.ftl", Map.of("doc", model, "opt", LatexRenderer.templateOptions(options)));

        assertThat(tex).contains("\\documentclass[10pt]{article}")
                .contains("\\definecolor{accent}{HTML}{1F3A5F}")
                .contains("100\\% \\{focused\\} on\\_backend")
                .contains("Intern, R\\&D Lab")
                .contains("Cut costs by 40\\% using C\\# \\& SQL")
                .contains("\\textbackslash{}input\\{/etc/passwd\\}")
                .doesNotContain("\\input{/etc/passwd}")
                // URL: '#' percent-encoded then LaTeX-escaped; visible text escaped normally.
                .contains("\\href{https://github.com/sami_ba\\%23x}{github.com/sami\\_ba\\#x}")
                .contains("Feb 2024 – Present");
        assertThat(tex.indexOf("\\section*{Skills}")).isLessThan(tex.indexOf("\\section*{Summary}"));
        assertThat(tex.indexOf("\\section*{Summary}")).isLessThan(tex.indexOf("\\section*{Experience}"));
        // Requested order first, then the remaining non-empty sections (projects/education/certs are empty).
        assertThat(DocumentModels.headings(model)).containsExactly("Skills", "Summary", "Experience", "Languages");
    }

    @Test
    void letterTemplateRendersFrenchDates() {
        LetterContent letter = new LetterContent("fr", "Nexa & Co", "Développeur", "Madame, Monsieur,",
                List.of("Paragraphe 1 avec 50%.", "Paragraphe 2."), "Cordialement,", "Sami", List.of());
        Map<String, Object> model = DocumentModels.letter(letter, cv("x").header(), LocalDate.of(2026, 10, 1));
        String tex = registry.render("ats-classic", "letter.tex.ftl",
                Map.of("doc", model, "opt", LatexRenderer.templateOptions(registry.resolveOptions("ats-classic", null))));
        assertThat(tex).contains("Nexa \\& Co").contains("1 octobre 2026").contains("Paragraphe 1 avec 50\\%.")
                .contains("Objet : candidature au poste de Développeur");
    }
}
