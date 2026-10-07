# Decisions Log

Choices made where `docs/PROJECT.md` was silent, ambiguous, or could not be followed on this
machine. Newest phase first.

## Phase 5.1 — Language toggle and speed (2026-10-01)

User feedback after testing Phase 5: generation is slow and some CVs mixed French and English.

- **Language is decided by code, not by llama3.** `LanguageGuess` counts EN/FR function words; the
  job's language comes from it when the text clearly leans one way (the model mislabelled an
  English offer asking for "fluent French"). The model's answer is only a tie-breaker.
- **Language check in the fact validator.** Summary and letter paragraphs written in the wrong
  language become a review flag (golden case `06-wrong-language.json`). Short bullets are not
  checked (too few words to decide).
- **Mixing came from copied facts.** Titles, descriptions, skill group names and spoken languages
  are copied from the profile (by design, so they cannot be invented); an English profile gave
  English labels in a French CV. When the profile language differs from the job language, the
  pipeline translates those labels (`DocumentTranslator`) after assembling the CV. Organisations,
  dates, links, skill items and names are never sent to the model.
- **FR ⇄ EN toggle** (not in PROJECT.md): `POST /api/documents/{id}/translate {language}` creates a
  **new version** in the other language (original kept), re-validated in the target language, same
  template and match score. Synchronous (a few minutes) like section regenerate. Texts already clearly in
  the target language are skipped; texts the model drops come back unchanged; batches of 25.
  Translation uses the main (8B) model: it is writing, quality matters.
- **Speed: two models.** `OLLAMA_FAST_MODEL` (dev: `llama3.2:3b`) runs job analysis and the match
  judgment (extraction / yes-partial-no); CV, letter, CV import and translation stay on 8B.
  Empty = main model for everything (PROJECT.md default).
- **Shorter CVs:** max 4 bullets per experience (was 5), 3 per project, 2-sentence summary — less
  to generate, and closer to one page.
- **`.gitattributes`** (`* text=auto eol=lf`): stops the LF/CRLF conversion warnings on Windows and keeps shell scripts and Dockerfiles LF.

## Phase 5 — LaTeX PDF (2026-10-01)

- **Engine: Tectonic (XeTeX) instead of TeX Live pdfLaTeX.** PROJECT.md allows "Tectonic or
  TeX Live"; a TeX Live image is several GB, impossible on this connection. Alpine's `tectonic`
  package is 6 MB; a build-time warm-up (`latex-worker/warmup/warmup.tex`, compiled at 10/11/12pt)
  caches exactly the packages/fonts/formats the templates use; runtime uses `--only-cached`.
  Image: ~150 MB. Alpine ships only the classic "V1" CLI (`tectonic file.tex`), not `-X compile`.
  With XeTeX + OpenType Latin Modern, text is Unicode natively, so `glyphtounicode` (a pdfLaTeX
  fix) is not needed; common ligatures are disabled (`Ligatures=NoCommon`) so "fi"/"fl" never
  become U+FB01/U+FB02 in extracted text. Manifest `engine` is `xetex`.
- **Worker sandbox** (compose): `internal` network (no internet; only the backend can reach it),
  read-only root FS + 256 MB tmpfs, non-root UID 1000, `cap_drop: ALL`, `no-new-privileges`,
  1 GB RAM, 1 CPU, 64 PIDs, 20 s compile timeout, one compile at a time, 2 MB request limit,
  only `.tex/.sty/.cls` with safe names, fresh temp dir per job.
- **Tested attacks against the real image**: `\immediate\write18{...}` → blocked by
  `--untrusted` (nothing executed). `\input{/etc/passwd}` and `\openin` **did read the file**:
  Tectonic's untrusted mode does not restrict absolute paths. Risk was low (the container holds
  no secrets, and all user/LLM text is LaTeX-escaped so only our templates contain commands), but
  the doc explicitly cites this attack, so the worker now rejects any submitted file using file
  I/O / catcode / Lua primitives or absolute/parent paths (400). `LatexPipelineIT` asserts it.
- **Template engine**: FreeMarker cannot use the doc's `<<= >>`/`<% %>` delimiters; its built-in
  square-bracket syntax (`[=x]`, `[#if]`) is used instead (neither `[=` nor `[#` occurs in
  LaTeX). A custom `LatexOutputFormat` makes **auto-escaping the default for every
  interpolation**, so model text cannot reach the .tex unescaped even if a template author
  forgets. URLs are the one pre-escaped value (`\href` needs percent-encoding + `\%`/`\#`), passed
  as FreeMarker markup. `?new`/`?api` are disabled in templates.
- **Options are validated against the manifest** (enum whitelist, `#RRGGBB` colours, bool,
  section-order lists restricted to the template's sections); unknown keys are ignored. The
  resolved options are stored on the document so any CV can be re-rendered exactly.
- **ATS self-check** (PDFBox): headings in order (30), email/phone as plain text (25), clean text
  — no ligature glyphs, no U+FFFD / `(cid:` garbage (25), page count ≤ manifest `maxPages` (20).
  Score + report stored in `ats_score`/`ats_report`; behind `features.ats-check` (now `true`).
- **Stale PDFs**: any content change (edit, section regenerate) clears `pdf_path`, `latex_source`
  and the ATS result; template + options are kept, the editor asks to re-render.
- Rendering is synchronous (a few seconds per document); PDFs stored under
  `storage/pdf/<applicationId>/<documentId>.pdf`. `*IT` tests (real worker via Testcontainers,
  built from `../latex-worker`) run in `mvn verify` through maven-failsafe.
- **Keeping the 8B model on a 4 GB GPU** (user choice after measuring): `llama3` 8B Q4 loads as
  6.1 GB — 2.7 GB on the GPU, the rest in system RAM. Imports failed (> 10 min → 504) because the
  PC had < 1.5 GB free: Ollama's idle runner held 8 GB and Docker's WSL VM ~6 GB. Changes on the
  user's machine (approved): `%USERPROFILE%\.wslconfig` `memory=3GB` (Docker VM 7.6 → 2.8 GB) and
  user env vars `OLLAMA_FLASH_ATTENTION=1`, `OLLAMA_KV_CACHE_TYPE=q8_0` (smaller context cache).
  In the app: `OLLAMA_FALLBACK_MODEL=llama3.2:3b` — when Ollama reports "requires more system
  memory", `LlmClient` retries that one call on the fallback instead of failing (the 3B model
  is weaker at writing letters, so the 8B stays the default). Backend HTTP read timeout lowered
  to 9 min (< nginx 10 min) so the backend, not nginx, answers with a clear message; the
  frontend explains 504s. The rest of the RAM is used by the user's own apps; closing the browser
  or Notion before generating lets the 8B model run.

## Phase 4 — Generation (2026-10-01)

- **Facts by construction, not by validation.** Prompt C returns only `{summary, experience:
  [{ref, bullets}], projects: [{ref, bullets}], skills: [{group, items}]}` with short evidence
  refs (`E1`, `P2`, `D1`, `C1`, `S1` — small models copy these reliably, unlike UUIDs).
  `ContentAssembler` copies name, contact, titles, organisations, dates, education,
  certifications and spoken languages from the profile snapshot by ref. So the doc's validator
  rules 1–2 (employers/schools exist, dates match) hold structurally; unknown refs are ignored
  and reported. Experiences the model forgets are appended (tailoring reorders, it never silently
  drops a real job); skills not in the profile are dropped, forgotten ones appended.
- **`FactValidator`** (3.6, rules 3–4 + extras) on everything the model wrote: numbers must appear
  in the profile; job-ad technologies (keywords + capitalised/acronym/digit tokens of the
  requirements, e.g. "Kubernetes", "CI/CD") must appear in the profile; a capitalised name after
  "at / for / with / joined / chez / pour / au sein de…" must be one of the user's organisations
  or the target company; skills-section items must exist in the profile. Violations → one
  corrective retry with the list appended as `<previous_violations>`; what remains becomes
  `review` flags shown per section in the editor (content is kept: the user decides). Golden-file
  tests in `src/test/resources/golden/validator/` (add a JSON file to add a case).
- **Prompt-injection hygiene for generation**: company, role, tone, candidate name, job analysis
  and evidence are all delimited DATA in the user message (delimiters stripped from content);
  only the controlled language name is substituted into system prompts.
- **Evidence pack**: every profile item gets a ref; relevance = best hybrid retrieval score over
  all requirements (Phase 3). Experience + education always included; other items by relevance
  within 9,000 characters; skill groups always (they are short). Summary/languages chunks are
  header facts, not citable items.
- **Match score (prompt E)**: one batched LLM call returns yes/partial/no per requirement (falls
  back to the retrieval score when the model skips one). Per requirement: `0.6 × verdict +
  0.4 × retrieval strength`; must-have weight 2, nice-to-have 1; a requirement with **no retrieved
  evidence is forced to "no"** whatever the model says. Gaps = requirements scoring < 0.4.
  Stored in the CV `content_json.match` (+ `generated_documents.match_score`); not printed on the CV.
- **Async jobs**: `@Async` on a dedicated single-thread executor (the local LLM serves one request
  at a time anyway). Job rows carry a `step` (EVIDENCE → MATCH → CV → LETTER → SAVING) for the
  progress bar; the editor polls every 3 s. Inputs are validated synchronously before queueing
  (clear 400s for empty profile / missing job). A **partial unique index** guarantees one active
  job per application (two quick clicks → 409). Jobs left QUEUED/RUNNING by a restart are marked
  FAILED at startup. V3 migration adds `created_at`, `step`, `cv_document_id`,
  `letter_document_id` to `generation_jobs`.
- **No DB transaction around LLM calls**: the pipeline reads a detached `ProfileSnapshot` in a short
  read-only transaction, runs the LLM steps, then saves documents in a short transaction.
- **Minimal applications in Phase 4**: `generated_documents.application_id` is NOT NULL, so
  generation needs an application. Added create-from-analysed-job, get and recent list; full
  tracker CRUD/filters/status workflow stays in Phase 6. New applications start as `DRAFT`
  (the schema default `PENDING` means "sent").
- **Editing**: each full generation creates a new version (FR-14); manual edits and section
  regeneration update the current version in place. Edits can change summary, bullets,
  experience order, which projects show, skill groups and letter text; facts and the match score
  are always taken from the stored document (to change a fact, edit the profile). Every save and
  regenerate re-runs the fact check, so review flags always describe the current text.
- **Section regenerate** (`POST /api/documents/{id}/regenerate-section`, sections `summary`,
  `skills`, `experience:E1`, `projects:P1`, `letter`) is synchronous (a few minutes) and uses
  temperature 0.7 to produce a different version; only the requested section is replaced.
- **Preview**: escaped HTML preview next to the editor until Phase 5 provides the PDF.
- **Fail fast on LLM errors**: first real run hit Ollama's "model requires more system memory
  (2.4 GiB) than is available (1.5 GiB)" (the PC had 0.8 GB free RAM). Spring AI's default retry
  (10 attempts, back-off up to minutes) kept the job "RUNNING" for 14+ minutes. Now
  `spring.ai.retry.max-attempts: 2` (2–10 s back-off) and `LlmClient` turns the out-of-memory error
  into a clear 503 ("close some apps and try again"). The restart recovery marked the stuck job
  FAILED as designed. **Not yet verified end to end with the real model** because of the RAM limit.

## Phase 3 — RAG core (2026-09-25)

- **Own pgvector SQL instead of Spring AI `PgVectorStore`.** The schema (PROJECT.md 2.4) already
  defines `profile_chunks` with `profile_id`/`item_id` foreign keys (cascade delete), while
  `PgVectorStore` manages its own table layout. The doc's `filterExpression("profile_id == '" +
  id + "'")` is also string concatenation. `ProfileChunkRepository` uses parameterised SQL with
  `<=>` (cosine distance, matches the HNSW `vector_cosine_ops` index); similarity = 1 − distance.
  Spring AI is still used for the `EmbeddingModel` (Ollama, nomic-embed-text).
- **Chunks** (3.3): one per experience/project/education/certification, one per skill group, plus
  "Profile summary" (headline + summary) and "Spoken languages" chunks. Self-contained text, so a
  chunk reads well as evidence. Metadata: `{type, item_id, org, start, end, content_hash}`.
- **Re-ingest on edit**: `ProfileChangedEvent` → `@TransactionalEventListener(AFTER_COMMIT)` +
  `@Async` on a single-thread executor. Unchanged chunks (same SHA-256 of content) reuse their
  stored embedding, so an edit re-embeds only what changed. Failures are logged; the old index
  stays until the next change or `POST /api/profile/reindex`.
- **Concurrent swaps**: automatic and manual re-index can overlap (a test caught duplicate
  chunks). `replaceAll` takes `pg_advisory_xact_lock(hashtextextended(profile_id))` so
  delete+insert per profile is serialised (also across backend instances).
- **Hybrid retrieval instead of "cosine ≥ 0.55"** (measured with the real model on the sample
  profile + sample JD). nomic-embed-text similarities sit in a narrow ~0.45–0.75 band and short
  chunks (skill lists, one-line summary) behave badly: "Angular" → no evidence although it is a
  listed skill (0.505), "Kubernetes" → the generic summary at 0.641 (false positive),
  "Engineering degree" → a project ranked above the education entry. `RetrievalService` now:
  takes the top-30 vector candidates, adds `0.35 × share of the requirement's key terms found in
  the chunk` (`LexicalMatcher`: EN/FR stopwords + generic job-ad words like "experience",
  "years", "fluent" ignored, plural folding, tech tokens like `c#`, `node.js`, `ci/cd` kept),
  and keeps a chunk only if it shares ≥1 key term with similarity ≥ 0.45, or has similarity ≥ 0.70
  alone. Top-4 by score. Result on the sample JD: 8/8 requirements correct (was 3/8).
  `HybridRetrievalTest` pins the measured numbers. Evidence DTOs expose `similarity`, `score`
  and `matchedTerms` so the UI can explain every match. Config: `jobpilot.rag.*`.
- **JD language is not trusted from the model**: llama3 answered "fr" for an English offer that
  asks for "fluent French". A clear EN/FR stop-word majority (≥ 5 hits and 2:1) wins; the model's
  answer only breaks ties. The language drives the language of the generated CV/letter.
- **nginx caching**: `index.html` (and the SPA fallback) is served with `Cache-Control: no-cache`,
  hashed `main-*/polyfills-*/styles-*/chunk-*` files with a 1-year immutable cache. Without it the
  browser kept an old `index.html` pointing at an old bundle after a deploy (seen in testing).
- **Extra endpoints** (not in PROJECT.md): `GET /api/profile/index` (chunk count),
  `POST /api/profile/reindex`, `GET /api/profile/search?q=` (raw top-k, no threshold) so the user
  can see what the AI retrieves; `GET /api/jobs`, `GET /api/jobs/{id}`,
  `GET /api/jobs/{id}/evidence` (per-requirement evidence — a preview of Phase 4 retrieval).
- **JD sanitising** (3.7), `JdSanitizer`: HTML → text with jsoup (scripts/styles/iframes dropped,
  list items kept as "- "), invisible/bidi/control characters removed, `<job>` delimiters
  removed, lines that address the AI ("ignore previous instructions", "SYSTEM:", "reveal your
  prompt"...) are **removed and reported** as warnings (shown in the UI). Limits: raw paste ≤
  100k chars (may contain HTML), cleaned text 100–20,000 chars (≈5k tokens, leaves room for the
  prompt and output in the 8k context). The doc says "about 8k tokens"; 8k would not fit.
- **Prompt B** in `resources/prompts/jd-analysis-system.txt`. Output is cleaned by
  `JobAnalysisCleaner`: language normalised to `en`/`fr` (falls back to a word-count guess),
  seniority to `intern|junior|mid|senior|lead|principal`, lists trimmed/deduped/capped.
  `job_descriptions.raw_text` stores the **sanitised** text (what the model actually saw).
- **Shared LLM plumbing**: `common.llm.LlmClient` (JSON mode, temperature 0, one retry, user-safe
  errors), `LlmJson` (tolerant mapper, JSON extraction, delimiter stripping) — used by prompts A
  and B, and by C/D in Phase 4.
- Spring MVC's own 4xx exceptions (bad params, 405...) are now mapped to their status instead of
  the catch-all 500.
- Tests use `FakeEmbeddingModel` (hashed bag-of-words, 768-d) registered as `@Primary` in
  `AbstractIntegrationTest`, so similarity search runs against real pgvector without Ollama.

## Phase 2 — Profile (2026-09-25)

### UI kit: Sneat (replaces the Phase 1 custom styles)
- User asked to use the **Sneat** admin template (their local copy `D:\PFE\sneat-1.0.0`, MIT
  license). Vendored only what is needed into `frontend/public/sneat/`: `core.css`,
  `theme-default.css`, `pages/page-auth.css`, Boxicons font + `LICENSE.md`. Loaded with `<link>`
  tags in `index.html` (critical-CSS inlining disabled in `angular.json` so the build does not
  try to read them from disk).
- Sneat's JS (jQuery-free `menu.js`, Bootstrap bundle) is **not** loaded. The shell reproduces
  Sneat's markup (vertical menu, detached navbar, auth card); mobile menu and user dropdown are
  driven by Angular signals.
- The user's Sneat copy is customised: **primary colour is `#ff3e1d`** (not stock `#696cff`).
  Kept as-is. Our own elements use `bg-primary`/`text-primary`, so the theme file is the single
  place to change the colour.
- `docs/style-reference.png` (Phase 1) is superseded by Sneat; kept only as an idea for the
  Phase 6 tracker table.

### LLM runtime
- **Native Ollama on Windows** (user choice): the machine already has Ollama 0.20 with
  `llama3:latest` (8B) and an RTX 2050 GPU. The backend calls
  `OLLAMA_BASE_URL=http://host.docker.internal:11434` (compose adds `extra_hosts` for Linux).
- Docker's `ollama` + `ollama-init` services are now behind the compose profile
  `docker-ollama`. `.env.example` enables it by default (matches PROJECT.md); the dev `.env`
  does not. `backend.depends_on.ollama` has `required: false`.
- Chat model is configurable (`OLLAMA_CHAT_MODEL`, default `llama3.1:8b` per doc). Dev uses
  `llama3:latest` to avoid a 4.9 GB download on a slow connection.
- `spring.ai.ollama.init.pull-model-strategy: never`; HTTP read timeout 5 min.

### CV parsing
- **tika-core + PDFBox + POI instead of the full Tika parser bundle.** `tika-core` (small) only
  detects the real file type from magic bytes; PDFBox extracts PDF text, POI extracts DOCX.
  The full `tika-parsers-standard-package` is hundreds of MB of transitive dependencies, and POI
  is needed for Excel export (Phase 6) anyway.
- Limits: 5 MB, PDF/DOCX only (by content, not by name), first 10 PDF pages, 30k chars of text.
  Encrypted PDFs and text-less (scanned) files return a clear 422.
- Prompt A lives in `backend/src/main/resources/prompts/cv-structuring-system.txt`. JSON mode,
  temperature 0, CV text wrapped in `<cv_text>` with any embedded `cv_text` tags removed. One
  retry on unparseable JSON, then 502 with a "fill in manually" message.
- The schema is slightly more concrete than the doc's: skills are **groups**
  (`{group, items[]}`), stored as `SKILL` items with `title = group`, `tags = skills` (PROJECT.md
  3.3 chunks "one per skill group"). Projects keep `tags` for technologies.
- `CvDraftMapper` treats the LLM output as untrusted: strips control characters, caps lengths and
  list sizes, only keeps http(s) links (bare domains get `https://`), drops empty entries,
  de-duplicates bullets/tags. `CvDates` parses EN/FR month names; "Present" → NULL end date;
  years outside 1950–2100 are rejected.
- **Import replaces the whole profile** (the UI asks for confirmation). The original file is kept
  under `storage/cv/<userId>/<uuid>.<ext>` (generated name); the previous file is deleted.
- **Import is synchronous** (one HTTP request, up to a few minutes). Async jobs arrive in
  Phase 4 for generation; nginx `proxy_read_timeout` is 300 s.
- `ProfileChangedEvent` is published on every change; Phase 3 re-embeds on it.

### API additions (not in PROJECT.md 2.6)
- `POST/PUT/DELETE /api/profile/items[/{id}]` and `PUT /api/profile/items/order` — the doc only
  lists `GET/PUT /api/profile`; item-level endpoints make section editing simple and safe.
  Ownership is checked in the query (`findOwned`) → another user's item is a 404.

### Windows test fix (also explains the Phase 1 RANDOM_PORT failure)
- The JDK could not open its NIO loopback pipe (`SocketException: Invalid argument: connect`)
  because `%TEMP%` resolves to the 8.3 short path `C:\Users\HADILS~1\...`. Adding the Ollama
  starter made every Spring context hit it. Fix: `-Djdk.net.unixdomain.tmpdir=<target dir>` in
  surefire `argLine` and `spring-boot:run` `jvmArguments` (quoted: the path has spaces). No
  effect on Linux/CI/Docker.

## Phase 1 — Foundation (fresh restart, 2026-09-25)

The first attempt was wiped at the user's request (still in git history at `8af36a8`) and rebuilt.

### Versions (user: "keep the versions already installed")
- **Java 17, not 21.** Only JDK 17 is installed. Spring Boot 3.5 fully supports 17. Dockerfile
  uses `maven:3.9-eclipse-temurin-17` / `eclipse-temurin:17-jre`.
- **Spring Boot 3.5.6, not 3.3.** Spring AI 1.x (needed from Phase 2/3) requires Boot 3.4+.
- **Angular 20, project-local (not the global CLI 15, not 18).** The global CLI 15 does not
  support Node 24 (installed) and has no signals. Angular 20 is pinned in
  `frontend/package.json` and run via `npx ng`, so the global install is untouched. User
  approved. Dockerfile builds with `node:24-alpine`; output is `dist/frontend/browser`.

### Backend
- **Refresh tokens.** The schema has no table for them though FR-1 requires refresh. Added
  `refresh_tokens` in `V1__init_schema.sql`. Tokens are opaque random values; only the
  SHA-256 hash is stored. Each refresh rotates (old revoked, new issued). Presenting an
  already-revoked token revokes *all* of that user's tokens (theft detection).
- **Token lifetimes.** Access JWT (HS256) 15 min, refresh 7 days.
- **JWT library.** `io.jsonwebtoken:jjwt` 0.12.6.
- **`profiles.user_id` is UNIQUE.** The doc says "one per user"; the constraint enforces it.
- **Extra indexes** on `refresh_tokens(user_id)`, `profile_items(profile_id)`,
  `applications(user_id, status)` — FK/filter columns used by every query.
- **Health check.** Spring Boot Actuator `/actuator/health` (public, no details).
- **`/api/auth/me` and `/api/auth/logout`** added (not in the doc's API table): the frontend
  needs the current user and a way to revoke the refresh token.
- **No CORS.** nginx serves the SPA and proxies `/api` on the same origin; `ng serve` does the
  same via `frontend/proxy.conf.json`. Simpler and safer than configuring CORS.
- **Feature flags** as `features.*` in `application.yml` (`FeatureProperties` record).
- **Integration tests use MockMvc** (`@SpringBootTest` MOCK env + `@AutoConfigureMockMvc`), not
  a real port: on this Windows machine embedded Tomcat on a random port failed with
  `SocketException: Invalid argument: connect` in the previous attempt (Docker Desktop network
  adapters). One shared pgvector Testcontainer per test run.
- **No Maven wrapper.** Maven 3.9.9 is installed locally and Docker/CI use their own Maven.

### Frontend
- **Token storage.** Access token only in memory; refresh token in `localStorage` so a page
  reload restores the session (via `provideAppInitializer`). Trade-off: an XSS could read the
  refresh token. Mitigations: Angular's default escaping, never binding model output as HTML,
  rotation + reuse detection. Revisit (httpOnly cookie) before any public deployment.
- **Visual style** follows `docs/style-reference.png` (user-provided): slim icon rail, bold page
  titles, white cards, pastel status pills, blue accent. Tokens live in `src/styles.scss`.
- **Nav items for later phases** are visible but disabled with the phase number.

### Docker Compose
- `latex-worker` is not in compose until Phase 5 (nothing to run yet).
- Postgres is also published on host port 5432 so `mvn spring-boot:run` can use it in dev.
- Phase 1 was verified with `docker compose up -d --build --no-deps db backend frontend`:
  Phase 1 does not call Ollama, and pulling the Ollama image + models is several GB. A plain
  `docker compose up` still starts everything, including the model download.
