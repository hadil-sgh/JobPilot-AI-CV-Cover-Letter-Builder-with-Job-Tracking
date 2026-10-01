package com.jobpilot.application;

/** Tracker status (Notion "Situation"). Workflow rules arrive with the tracker in Phase 6. */
public enum ApplicationStatus {
    DRAFT,
    PENDING,
    INTERVIEW,
    OFFER,
    REJECTED,
    GHOSTED,
    WITHDRAWN
}
