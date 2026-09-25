package com.jobpilot.job;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.job.JdSanitizer.SanitizedJd;
import com.jobpilot.job.dto.JobDtos.EvidenceHit;
import com.jobpilot.job.dto.JobDtos.EvidenceReport;
import com.jobpilot.job.dto.JobDtos.JobDto;
import com.jobpilot.job.dto.JobDtos.JobSummary;
import com.jobpilot.job.dto.JobDtos.RequirementEvidence;
import com.jobpilot.profile.ProfileService;
import com.jobpilot.rag.ProfileChunkRepository;
import com.jobpilot.rag.RetrievalService;

/**
 * JD analysis (prompt B) and per-requirement evidence retrieval. The slow LLM call runs outside
 * any DB transaction; only the save is transactional (repository default).
 */
@Service
public class JobService {

    private final JdSanitizer sanitizer;
    private final JobAnalyzer analyzer;
    private final JobDescriptionRepository jobs;
    private final ProfileService profiles;
    private final RetrievalService retrieval;
    private final ProfileChunkRepository chunks;

    public JobService(JdSanitizer sanitizer, JobAnalyzer analyzer, JobDescriptionRepository jobs,
                      ProfileService profiles, RetrievalService retrieval, ProfileChunkRepository chunks) {
        this.sanitizer = sanitizer;
        this.analyzer = analyzer;
        this.jobs = jobs;
        this.profiles = profiles;
        this.retrieval = retrieval;
        this.chunks = chunks;
    }

    public JobDto analyze(UUID userId, String rawText, String sourceUrl) {
        SanitizedJd jd = sanitizer.sanitize(rawText);
        JobAnalysis analysis = JobAnalysisCleaner.clean(analyzer.analyze(jd.text()), jd.text());
        JobDescription saved = jobs.save(new JobDescription(userId, jd.text(), analysis, sourceUrl));
        return toDto(saved, jd.warnings());
    }

    public JobDto get(UUID userId, UUID jobId) {
        return toDto(owned(userId, jobId), List.of());
    }

    public List<JobSummary> recent(UUID userId) {
        return jobs.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(j -> new JobSummary(j.getId(), j.getAnalysis() == null ? null : j.getAnalysis().title(),
                        j.getAnalysis() == null ? null : j.getAnalysis().company(), j.getLanguage(), j.getCreatedAt()))
                .toList();
    }

    /** For each requirement (must-have, then nice-to-have), the best matching profile chunks. */
    public EvidenceReport evidence(UUID userId, UUID jobId) {
        JobAnalysis analysis = owned(userId, jobId).getAnalysis();
        UUID profileId = profiles.profileIdOf(userId);
        List<RequirementEvidence> out = new ArrayList<>();
        if (analysis != null) {
            analysis.requirements().forEach(r -> out.add(new RequirementEvidence(r, true, hits(r, profileId))));
            analysis.niceToHave().forEach(r -> out.add(new RequirementEvidence(r, false, hits(r, profileId))));
        }
        return new EvidenceReport(jobId, chunks.count(profileId), retrieval.minSimilarity(), out);
    }

    private List<EvidenceHit> hits(String requirement, UUID profileId) {
        return retrieval.evidenceFor(requirement, profileId).stream()
                .map(e -> new EvidenceHit(e.hit().itemId(), String.valueOf(e.hit().metadata().get("type")),
                        e.hit().content(), round(e.hit().similarity()), round(e.score()), e.matchedTerms()))
                .toList();
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private JobDescription owned(UUID userId, UUID jobId) {
        return jobs.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job description not found"));
    }

    private static JobDto toDto(JobDescription j, List<String> warnings) {
        return new JobDto(j.getId(), j.getLanguage(), j.getSourceUrl(), j.getCreatedAt(), j.getAnalysis(),
                j.getRawText(), warnings);
    }
}
