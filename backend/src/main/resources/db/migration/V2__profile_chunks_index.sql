-- Retrieval always filters by profile; the HNSW index handles the vector ordering.
CREATE INDEX idx_profile_chunks_profile ON profile_chunks(profile_id);
CREATE INDEX idx_job_descriptions_user ON job_descriptions(user_id, created_at DESC);
