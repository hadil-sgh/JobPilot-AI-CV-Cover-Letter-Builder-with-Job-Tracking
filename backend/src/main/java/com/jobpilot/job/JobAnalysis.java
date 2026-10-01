package com.jobpilot.job;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Structured job description (prompt B output, stored in job_descriptions.analysis).
 * Untrusted when it comes from the LLM: {@link JobAnalysisCleaner} sanitises it before storage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobAnalysis(
        String title,
        String company,
        String language,
        String seniority,
        List<String> requirements,
        @JsonAlias("nice_to_have") List<String> niceToHave,
        List<String> keywords,
        List<String> responsibilities,
        String tone) {
}
