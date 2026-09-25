-- Full schema from docs/PROJECT.md section 2.4, plus refresh_tokens (see docs/DECISIONS.md).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(255),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex; the raw token is never stored
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked     BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Master profile (one per user)
CREATE TABLE profiles (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  headline    VARCHAR(255),
  summary     TEXT,
  phone       VARCHAR(50),
  location    VARCHAR(255),
  links       JSONB DEFAULT '[]',        -- linkedin, github, portfolio
  languages   JSONB DEFAULT '[]',
  original_file_path VARCHAR(500),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profile_items (             -- experience, education, project, skill, certification
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  type        VARCHAR(30) NOT NULL,      -- EXPERIENCE | EDUCATION | PROJECT | SKILL | CERTIFICATION
  title       VARCHAR(255),
  organization VARCHAR(255),
  start_date  DATE,
  end_date    DATE,
  description TEXT,
  bullets     JSONB DEFAULT '[]',
  tags        JSONB DEFAULT '[]',
  sort_order  INT DEFAULT 0
);
CREATE INDEX idx_profile_items_profile ON profile_items(profile_id);

-- Vector store: chunks of the profile used for retrieval
CREATE TABLE profile_chunks (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  item_id     UUID REFERENCES profile_items(id) ON DELETE CASCADE,
  content     TEXT NOT NULL,
  metadata    JSONB DEFAULT '{}',
  embedding   vector(768) NOT NULL
);
CREATE INDEX idx_profile_chunks_embedding ON profile_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE job_descriptions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  raw_text    TEXT NOT NULL,
  language    VARCHAR(5),
  analysis    JSONB,                     -- {title, requirements[], nice_to_have[], keywords[], seniority}
  source_url  VARCHAR(1000),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The tracker. Mirrors the Notion columns.
CREATE TABLE applications (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  job_id        UUID REFERENCES job_descriptions(id),
  applied_date  DATE,                    -- Notion: Date
  company       VARCHAR(255) NOT NULL,   -- Notion: Company
  role_title    VARCHAR(255),
  country       VARCHAR(100),            -- Notion: Country
  work_mode     VARCHAR(20),             -- REMOTE | HYBRID | ONSITE  (Notion: Remote)
  summary       TEXT,                    -- Notion: summary
  description   TEXT,                    -- Notion: Description (the JD)
  my_answers    TEXT,                    -- Notion: My Answers
  status        VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- Notion: Situation
  contact_email VARCHAR(255),
  notion_page_id VARCHAR(64),
  notion_synced_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- status: DRAFT | PENDING | INTERVIEW | OFFER | REJECTED | GHOSTED | WITHDRAWN
CREATE INDEX idx_applications_user_status ON applications(user_id, status);

CREATE TABLE generated_documents (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  type          VARCHAR(20) NOT NULL,    -- CV | COVER_LETTER
  version       INT NOT NULL DEFAULT 1,
  language      VARCHAR(5),
  template      VARCHAR(50),             -- template id from manifest, e.g. 'ats-classic'
  template_version VARCHAR(20),
  template_options JSONB DEFAULT '{}',
  content_json  JSONB NOT NULL,
  latex_source  TEXT,
  pdf_path      VARCHAR(500),
  match_score   INT,
  ats_score     INT,
  ats_report    JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE generation_jobs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID REFERENCES applications(id) ON DELETE CASCADE,
  status        VARCHAR(20) NOT NULL,    -- QUEUED | RUNNING | DONE | FAILED
  error         TEXT,
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ
);

CREATE TABLE email_accounts (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider      VARCHAR(20) NOT NULL,    -- GMAIL | SMTP
  address       VARCHAR(255) NOT NULL,
  encrypted_credentials BYTEA NOT NULL
);

CREATE TABLE email_messages (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID REFERENCES applications(id) ON DELETE CASCADE,
  direction     VARCHAR(10) NOT NULL,    -- SENT | RECEIVED
  subject       VARCHAR(500),
  body_excerpt  TEXT,
  external_id   VARCHAR(255),
  detected_status VARCHAR(30),
  received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
