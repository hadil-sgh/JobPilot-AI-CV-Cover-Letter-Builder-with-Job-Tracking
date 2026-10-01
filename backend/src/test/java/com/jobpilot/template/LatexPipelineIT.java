package com.jobpilot.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.DocumentModels;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;

/**
 * Real end-to-end LaTeX pipeline: FreeMarker template → the actual latex-worker image (built from
 * ../latex-worker with Testcontainers, same sandbox flags as compose) → PDF → ATS self-check.
 */
class LatexPipelineIT {

    /**
     * Built from ../latex-worker (as in CI). Locally, set LATEX_WORKER_IMAGE=jobpilot-latex-worker:dev to reuse an
     * already-built image: Testcontainers' builder cannot reuse BuildKit's cache and would re-download packages.
     */
    @SuppressWarnings("resource")
    static final GenericContainer<?> WORKER = (System.getenv("LATEX_WORKER_IMAGE") != null
            ? new GenericContainer<>(org.testcontainers.utility.DockerImageName.parse(System.getenv("LATEX_WORKER_IMAGE")))
            : new GenericContainer<>(new ImageFromDockerfile("jobpilot-latex-worker-test", false)
                    .withFileFromPath(".", Path.of("../latex-worker"))))
            .withExposedPorts(8090)
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                    .withReadonlyRootfs(true)
                    .withTmpFs(Map.of("/tmp", "rw,size=256m,uid=1000"))
                    .withCapDrop(com.github.dockerjava.api.model.Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges"))
                    .withMemory(1024L * 1024 * 1024))
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    static ClasspathTemplateRegistry registry;
    static LatexRenderer renderer;
    static final AtsChecker ATS = new AtsChecker();

    @BeforeAll
    static void start() throws Exception {
        WORKER.start();
        registry = new ClasspathTemplateRegistry(new ObjectMapper());
        renderer = new LatexRenderer(registry,
                new LatexWorkerClient("http://" + WORKER.getHost() + ":" + WORKER.getMappedPort(8090), new ObjectMapper()));
    }

    @Test
    void rendersAnAtsFriendlyOnePageCv() {
        CvContent cv = TemplateRegistryTest.cv("Backend developer: finance & R&D (100% remote), office—ready, “quotes”, "
                + "efficient workflow with offices in Zürich.");
        Map<String, Object> options = registry.resolveOptions("ats-classic", Map.of("fontSize", "11pt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> model = DocumentModels.cv(cv, (List<String>) options.get("sectionOrder"));

        DocumentRenderer.Rendered r = renderer.render("ats-classic", "cv", model, options);
        AtsChecker.Report report = ATS.check(r.pdf(), DocumentModels.headings(model),
                List.of("sami@example.com", "+216 20 123 456"), 1);

        assertThat(report.checks()).allSatisfy(c -> assertThat(c.passed()).as(c.label() + ": " + c.detail()).isTrue());
        assertThat(report.score()).isEqualTo(100);
        assertThat(report.pageCount()).isEqualTo(1);
        // Words with fi/ffi/fl survive extraction (ligatures disabled); escaped text is printed literally.
        assertThat(report.extractedPreview()).contains("Sami Ben Ali").contains("finance").contains("R&D")
                .contains("100%");
    }

    @Test
    void rendersAllFontSizesOffline() {
        for (String size : List.of("10pt", "12pt")) {
            Map<String, Object> options = registry.resolveOptions("ats-classic", Map.of("fontSize", size, "density", "airy"));
            @SuppressWarnings("unchecked")
            Map<String, Object> model = DocumentModels.cv(TemplateRegistryTest.cv("Short."), (List<String>) options.get("sectionOrder"));
            assertThat(renderer.render("ats-classic", "cv", model, options).pdf()).startsWith((byte) '%', (byte) 'P');
        }
    }

    @Test
    void rendersTheLetterWithTheSameStyle() {
        LetterContent letter = new LetterContent("fr", "Nexa Fintech", "Développeur Java", "Madame, Monsieur,",
                List.of("Ingénieur, j'ai développé des API REST avec Spring Boot.", "Je serais ravi de rejoindre votre équipe."),
                "Je vous prie d'agréer mes salutations distinguées.", "Sami Ben Ali", List.of());
        Map<String, Object> model = DocumentModels.letter(letter, TemplateRegistryTest.cv("x").header(), LocalDate.of(2026, 10, 1));
        DocumentRenderer.Rendered r = renderer.render("ats-classic", "letter", model, Map.of());
        AtsChecker.Report report = ATS.check(r.pdf(), List.of(), List.of("sami@example.com"), 1);
        assertThat(report.score()).isEqualTo(100);
        assertThat(report.extractedPreview()).contains("Nexa Fintech").contains("1 octobre 2026").contains("équipe");
    }

    @Test
    void workerRejectsFileIoEvenIfATemplateTriedIt() {
        LatexWorkerClient client = new LatexWorkerClient("http://" + WORKER.getHost() + ":" + WORKER.getMappedPort(8090),
                new ObjectMapper());
        assertThatThrownBy(() -> client.compile("x.tex", Map.of("x.tex",
                "\\documentclass[11pt]{article}\\begin{document}\\input{/etc/passwd}\\end{document}")))
                .isInstanceOf(ApiException.class);
    }
}
