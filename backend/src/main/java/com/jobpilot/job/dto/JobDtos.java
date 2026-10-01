package com.jobpilot.job.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.jobpilot.job.JobAnalysis;

public final class JobDtos {

    private JobDtos() {
    }

    /** The raw paste may contain HTML, so it can be bigger than the cleaned-text limit. */
    public record AnalyzeRequest(
            @NotBlank @Size(max = 100_000) String text,
            @Size(max = 1000) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String sourceUrl) {
    }

    public record JobDto(UUID id, String language, String sourceUrl, Instant createdAt, JobAnalysis analysis,
                         String text, List<String> warnings) {
    }

    public record JobSummary(UUID id, String title, String company, String language, Instant createdAt) {
    }

    public record EvidenceHit(UUID itemId, String type, String content, double similarity, double score,
                              List<String> matchedTerms) {
    }

    public record RequirementEvidence(String requirement, boolean mustHave, List<EvidenceHit> evidence) {
    }

    public record EvidenceReport(UUID jobId, int profileChunks, double minSimilarity,
                                 List<RequirementEvidence> requirements) {
    }
}
