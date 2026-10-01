-- Async generation (Phase 4): progress step for polling, creation time, and the documents a job produced.
ALTER TABLE generation_jobs
  ADD COLUMN created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN step               VARCHAR(50),
  ADD COLUMN cv_document_id     UUID REFERENCES generated_documents(id) ON DELETE SET NULL,
  ADD COLUMN letter_document_id UUID REFERENCES generated_documents(id) ON DELETE SET NULL;

CREATE INDEX idx_generation_jobs_application ON generation_jobs(application_id, created_at DESC);
CREATE INDEX idx_generated_documents_application ON generated_documents(application_id, type, version DESC);
CREATE INDEX idx_applications_user_created ON applications(user_id, created_at DESC);

-- At most one active (queued/running) generation per application, enforced by the database.
CREATE UNIQUE INDEX uq_generation_jobs_active ON generation_jobs(application_id)
  WHERE status IN ('QUEUED', 'RUNNING');
