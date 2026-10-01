package com.jobpilot.generation.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw (untrusted) outputs of prompts C, D and E. */
public final class GenerationModels {

    private GenerationModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CvDraftOut(String summary, List<RefBullets> experience, List<RefBullets> projects,
                             List<SkillGroupOut> skills) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefBullets(String ref, List<String> bullets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillGroupOut(String group, List<String> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LetterDraftOut(String greeting, List<String> paragraphs, String closing) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verdicts(List<Verdict> verdicts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verdict(Integer id, String verdict) {
    }
}
