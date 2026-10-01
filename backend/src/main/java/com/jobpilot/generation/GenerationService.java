package com.jobpilot.generation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.jobpilot.application.ApplicationService;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.GenerationJob.Status;

/** Starts generation jobs and reports their state. */
@Service
public class GenerationService {

    public record JobDto(UUID id, UUID applicationId, Status status, String step, String error, Instant createdAt,
                         Instant startedAt, Instant finishedAt, UUID cvDocumentId, UUID letterDocumentId) {

        static JobDto of(GenerationJob j) {
            return new JobDto(j.getId(), j.getApplicationId(), j.getStatus(), j.getStep(), j.getError(),
                    j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(), j.getCvDocumentId(), j.getLetterDocumentId());
        }
    }

    private final GenerationJobRepository jobs;
    private final GenerationPipeline pipeline;
    private final GenerationRunner runner;
    private final ApplicationService applications;

    public GenerationService(GenerationJobRepository jobs, GenerationPipeline pipeline, GenerationRunner runner,
                             ApplicationService applications) {
        this.jobs = jobs;
        this.pipeline = pipeline;
        this.runner = runner;
        this.applications = applications;
    }

    /** Validates inputs synchronously (clear 4xx errors), then queues the async job. Not transactional. */
    public JobDto start(UUID userId, UUID applicationId) {
        GenerationContext ctx = pipeline.loadContext(userId, applicationId); // ownership + profile/job checks
        if (jobs.existsByApplicationIdAndStatusIn(applicationId, List.of(Status.QUEUED, Status.RUNNING))) {
            throw new ApiException(HttpStatus.CONFLICT, "A generation is already running for this application");
        }
        GenerationJob job;
        try {
            job = jobs.saveAndFlush(new GenerationJob(applicationId));
        } catch (DataIntegrityViolationException e) { // partial unique index: one active job per application
            throw new ApiException(HttpStatus.CONFLICT, "A generation is already running for this application");
        }
        runner.run(job.getId(), ctx);
        return JobDto.of(job);
    }

    public JobDto get(UUID userId, UUID jobId) {
        return jobs.findOwned(jobId, userId).map(JobDto::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Generation job not found"));
    }

    public Optional<JobDto> latest(UUID userId, UUID applicationId) {
        applications.owned(userId, applicationId);
        return jobs.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).map(JobDto::of);
    }
}
