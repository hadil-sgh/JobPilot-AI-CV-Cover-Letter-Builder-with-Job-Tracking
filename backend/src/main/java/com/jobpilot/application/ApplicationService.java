package com.jobpilot.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.common.error.ApiException;
import com.jobpilot.common.text.TextClean;
import com.jobpilot.job.JobAnalysis;
import com.jobpilot.job.JobDescription;
import com.jobpilot.job.JobDescriptionRepository;

/**
 * Minimal application handling needed by generation (Phase 4): create from an analysed job, read.
 * The full tracker (list/filter/patch/status workflow) is Phase 6.
 */
@Service
@Transactional
public class ApplicationService {

    public record CreateRequest(
            @NotNull UUID jobId,
            @Size(max = 255) String company,
            @Size(max = 255) String roleTitle,
            @Size(max = 100) String country,
            WorkMode workMode) {
    }

    public record ApplicationDto(UUID id, UUID jobId, String company, String roleTitle, String country,
                                 WorkMode workMode, ApplicationStatus status, String language,
                                 Instant createdAt, Instant updatedAt) {
    }

    private final ApplicationRepository applications;
    private final JobDescriptionRepository jobs;

    public ApplicationService(ApplicationRepository applications, JobDescriptionRepository jobs) {
        this.applications = applications;
        this.jobs = jobs;
    }

    public ApplicationDto create(UUID userId, CreateRequest req) {
        JobDescription job = jobs.findByIdAndUserId(req.jobId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job description not found"));
        JobAnalysis a = job.getAnalysis();
        String company = firstNonBlank(req.company(), a == null ? null : a.company(), "Unknown company");
        Application app = new Application(userId, TextClean.clean(company, 255));
        app.setJobId(job.getId());
        app.setRoleTitle(TextClean.clean(firstNonBlank(req.roleTitle(), a == null ? null : a.title(), null), 255));
        app.setCountry(TextClean.clean(req.country(), 100));
        app.setWorkMode(req.workMode());
        app.setDescription(job.getRawText());
        return toDto(applications.save(app), job.getLanguage());
    }

    @Transactional(readOnly = true)
    public ApplicationDto get(UUID userId, UUID id) {
        Application app = owned(userId, id);
        return toDto(app, languageOf(app));
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> recent(UUID userId) {
        return applications.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(a -> toDto(a, languageOf(a))).toList();
    }

    /** Ownership-checked load for other modules (generation, documents). */
    @Transactional(readOnly = true)
    public Application owned(UUID userId, UUID id) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private String languageOf(Application app) {
        return app.getJobId() == null ? null
                : jobs.findById(app.getJobId()).map(JobDescription::getLanguage).orElse(null);
    }

    private static ApplicationDto toDto(Application a, String language) {
        return new ApplicationDto(a.getId(), a.getJobId(), a.getCompany(), a.getRoleTitle(), a.getCountry(),
                a.getWorkMode(), a.getStatus(), language, a.getCreatedAt(), a.getUpdatedAt());
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
