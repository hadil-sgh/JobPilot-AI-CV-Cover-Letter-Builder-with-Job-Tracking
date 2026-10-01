package com.jobpilot.generation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An async CV + letter generation run (polled by the editor). */
@Entity
@Table(name = "generation_jobs")
public class GenerationJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(length = 50)
    private String step;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cv_document_id")
    private UUID cvDocumentId;

    @Column(name = "letter_document_id")
    private UUID letterDocumentId;

    protected GenerationJob() {
    }

    public GenerationJob(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public void start() {
        status = Status.RUNNING;
        startedAt = Instant.now();
    }

    public void step(String step) {
        this.step = step;
    }

    public void done(UUID cvDocumentId, UUID letterDocumentId) {
        this.cvDocumentId = cvDocumentId;
        this.letterDocumentId = letterDocumentId;
        status = Status.DONE;
        step = null;
        finishedAt = Instant.now();
    }

    public void fail(String error) {
        this.error = error;
        status = Status.FAILED;
        finishedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.RUNNING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public Status getStatus() {
        return status;
    }

    public String getStep() {
        return step;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public UUID getCvDocumentId() {
        return cvDocumentId;
    }

    public UUID getLetterDocumentId() {
        return letterDocumentId;
    }
}
