package com.jobpilot.generation.content;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Structured motivation letter (prompt D output after validation), editable in the UI. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LetterContent(
        String language,
        String company,
        String roleTitle,
        String greeting,
        List<String> paragraphs,
        String closing,
        String signature,
        List<ReviewFlag> review) {

    public LetterContent withReview(List<ReviewFlag> r) {
        return new LetterContent(language, company, roleTitle, greeting, paragraphs, closing, signature, r);
    }
}
