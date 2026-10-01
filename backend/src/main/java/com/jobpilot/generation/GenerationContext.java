package com.jobpilot.generation;

import java.util.UUID;

import com.jobpilot.job.JobAnalysis;

/** Everything one generation needs, loaded once (detached from the DB). */
public record GenerationContext(
        UUID userId,
        UUID applicationId,
        String company,
        String roleTitle,
        JobAnalysis analysis,
        String language,
        ProfileSnapshot profile) {

    public String tone() {
        return analysis.tone() == null ? "professional" : analysis.tone();
    }
}
