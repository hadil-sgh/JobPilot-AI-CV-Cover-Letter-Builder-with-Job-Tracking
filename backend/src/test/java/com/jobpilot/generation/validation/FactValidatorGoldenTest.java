package com.jobpilot.generation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.jobpilot.generation.GenerationContext;
import com.jobpilot.generation.ProfileSnapshot;
import com.jobpilot.generation.content.CvContent;
import com.jobpilot.generation.content.LetterContent;
import com.jobpilot.generation.content.ReviewFlag;
import com.jobpilot.job.JobAnalysis;

/**
 * Golden-file tests for the anti-hallucination validator (PROJECT.md 5, testing strategy).
 * Each file in src/test/resources/golden/validator holds a profile, a job, a generated CV or letter
 * and the exact findings expected ("section|message prefix"). Add a file to add a case.
 */
class FactValidatorGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final FactValidator validator = new FactValidator();

    static Stream<Path> cases() throws IOException {
        return Files.list(Path.of("src/test/resources/golden/validator")).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matchesGoldenFindings(Path file) throws IOException {
        JsonNode g;
        try (InputStream in = Files.newInputStream(file)) {
            g = JSON.readTree(in);
        }
        JobAnalysis analysis = new JobAnalysis("Role", g.get("company").asText(), "en", null,
                strings(g.get("requirements")), List.of(), strings(g.get("keywords")), List.of(), null);
        ProfileSnapshot profile = new ProfileSnapshot(UUID.randomUUID(), "Sami Ben Ali", "s@example.com", null, null,
                null, null, List.of(), List.of(), List.of(), java.util.Map.of(), g.get("profileText").asText());
        GenerationContext ctx = new GenerationContext(UUID.randomUUID(), UUID.randomUUID(), g.get("company").asText(),
                "Backend Developer", analysis, "en", profile);
        FactBase facts = FactBase.of(ctx);

        List<ReviewFlag> flags = g.has("cv")
                ? validator.validateCv(JSON.treeToValue(g.get("cv"), CvContent.class), facts)
                : validator.validateLetter(JSON.treeToValue(g.get("letter"), LetterContent.class), facts);

        List<String> actual = flags.stream().map(f -> f.section() + "|" + f.message()).toList();
        List<String> expected = strings(g.get("expected"));
        assertThat(actual).as(g.get("description").asText()).hasSameSizeAs(expected);
        for (String e : expected) {
            assertThat(actual).as(g.get("description").asText()).anyMatch(a -> a.startsWith(e));
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
