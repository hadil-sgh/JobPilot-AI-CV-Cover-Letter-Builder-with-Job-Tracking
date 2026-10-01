package com.jobpilot.generation;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jobpilot.common.config.AsyncConfig;
import com.jobpilot.common.error.ApiException;
import com.jobpilot.generation.GenerationJob.Status;

/**
 * Executes generation jobs on the single "generation" thread and records progress, so the editor
 * can poll {@code GET /api/generation-jobs/{id}}. Jobs left QUEUED/RUNNING by a restart are failed
 * on startup (their thread is gone).
 */
@Component
public class GenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(GenerationRunner.class);

    private final GenerationJobRepository jobs;
    private final GenerationPipeline pipeline;
    private final DocumentStore store;

    public GenerationRunner(GenerationJobRepository jobs, GenerationPipeline pipeline, DocumentStore store) {
        this.jobs = jobs;
        this.pipeline = pipeline;
        this.store = store;
    }

    @Async(AsyncConfig.GENERATION)
    public void run(UUID jobId, GenerationContext ctx) {
        update(jobId, GenerationJob::start);
        try {
            GenerationPipeline.Result result = pipeline.run(ctx, step -> update(jobId, j -> j.step(step)));
            DocumentStore.Saved saved = store.saveNewVersions(ctx.applicationId(), result.cv(), result.letter());
            update(jobId, j -> j.done(saved.cvId(), saved.letterId()));
            log.info("Generation job {} done", jobId);
        } catch (ApiException e) {
            log.warn("Generation job {} failed: {}", jobId, e.getMessage());
            update(jobId, j -> j.fail(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Generation job {} crashed", jobId, e);
            update(jobId, j -> j.fail("Unexpected error during generation. Please try again."));
        }
    }

    private void update(UUID jobId, java.util.function.Consumer<GenerationJob> change) {
        jobs.findById(jobId).ifPresent(j -> {
            change.accept(j);
            jobs.save(j);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedJobs() {
        int n = jobs.failAll(List.of(Status.QUEUED, Status.RUNNING), "Interrupted by a server restart. Please generate again.");
        if (n > 0) {
            log.warn("Marked {} interrupted generation job(s) as failed", n);
        }
    }
}
