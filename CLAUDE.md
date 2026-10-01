# JobPilot

Paste a job description, get a tailored, fact-checked CV and motivation letter compiled from
LaTeX into ATS-friendly PDFs, track applications (synced to Notion), send by email.

- **Source of truth:** [docs/PROJECT.md](docs/PROJECT.md) (architecture, schema, API, prompts,
  RAG, LaTeX templates, roadmap). If ambiguous, follow the doc; if silent, pick the simplest
  option and log it in [docs/DECISIONS.md](docs/DECISIONS.md).
- **UI kit: Sneat** (Bootstrap 5 admin template, MIT) vendored in `frontend/public/sneat/` and
  loaded in `index.html`. Build pages from Sneat classes/markup (`card`, `btn`, `form-control`,
  `badge bg-label-*`, `menu-*`, `avatar`). No Sneat/Bootstrap JS: interactivity via signals.
  The user's copy uses primary `#ff3e1d`. Reference pages: `D:/PFE/sneat-1.0.0/html/*.html`.

## Stack (versions = what is installed on the dev machine)

- Backend: Spring Boot 3.5.6, **Java 17**, Spring Security + JWT (jjwt), Spring Data JPA, Flyway,
  Spring AI (Ollama + pgvector, from Phase 2/3), Apache Tika/PDFBox, Apache POI.
- Frontend: **Angular 20** pinned in `frontend/package.json` (standalone components, signals,
  functional guards/interceptors). Use `npx ng`, never the global CLI (it is v15).
- DB: PostgreSQL 16 + pgvector (`pgvector/pgvector:pg16`), migrations in
  `backend/src/main/resources/db/migration`.
- LLM: Llama via Ollama. Dev uses the **native Windows Ollama** (GPU) at
  `http://host.docker.internal:11434` with `llama3:latest` (8B) and `OLLAMA_FALLBACK_MODEL=llama3.2:3b`
  used automatically when RAM is short (see DECISIONS.md Phase 5: .wslconfig 3 GB, Ollama q8 KV cache); Docker Ollama is opt-in
  (`COMPOSE_PROFILES=docker-ollama`). Model names come from `.env` (`OLLAMA_CHAT_MODEL`, ...).
- PDF: LaTeX via **Tectonic (XeTeX)** in the sandboxed `latex-worker` container (Alpine, ~150 MB;
  packages cached at build by `latex-worker/warmup/warmup.tex` — add new packages there).
- Node 24, Maven 3.9.9, Docker Desktop 27.

## Commands

```bash
# Backend (backend/) — Docker Desktop must be running for Testcontainers
mvn compile
mvn test                       # unit + integration (pgvector Testcontainer)
mvn verify                     # + *IT tests (builds/starts the real latex-worker image)
mvn spring-boot:run            # needs Postgres on localhost:5432 and JWT_SECRET env var

# Frontend (frontend/)
npm install
npx ng serve                   # http://localhost:4200, proxies /api to :8080
npx ng build
npx ng test --watch=false --browsers=ChromeHeadless

# Everything (root; copy .env.example to .env first)
docker compose up -d --build   # dev .env: db + backend + frontend; LLM = native Ollama on the host
```

App: http://localhost:4200 · API: http://localhost:8080 · Health: `/actuator/health`.

## Package layout

Backend `com.jobpilot`:
- `auth` — users, JWT (`JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`), rotating
  refresh tokens, `/api/auth/{register,login,refresh,logout,me}`. Controllers get the user via
  `@AuthenticationPrincipal AuthUser`.
- `common.error` — `ApiException` (safe client message) + `GlobalExceptionHandler`.
- `common.config` — `FeatureProperties` (`features.*` flags).
- `profile` — `Profile`/`ProfileItem` (JSONB lists), `ProfileService` (CRUD; ownership via
  `findOwned`), `ProfileImportService` (upload → text → LLM → profile; LLM call runs outside the
  DB transaction), `ProfileChangedEvent`. `profile.cv` — `CvTextExtractor` (tika-core type
  detection, PDFBox/POI), `CvStructurer`/`OllamaCvStructurer` (prompt A in
  `resources/prompts/`), `CvDraftMapper` + `CvDates` (sanitise untrusted LLM JSON),
  `CvFileStorage`. Tests mock `CvStructurer` with `@MockitoBean`: never call Ollama in tests.
- `rag` — `ProfileChunker`, `EmbeddingService` (nomic prefixes, 768-d check),
  `ProfileChunkRepository` (parameterised pgvector SQL, advisory-locked swap), `ProfileIndexer`
  (async re-index after commit, reuses unchanged embeddings), `RetrievalService` (top-k 4,
  min 0.55), `RagController` (`/api/profile/index|reindex|search`).
- `job` — `JdSanitizer` (HTML→text, injection-line removal, length limits), `JobAnalyzer` /
  `OllamaJobAnalyzer` (prompt B), `JobAnalysisCleaner`, `JobService` (analyze, evidence per
  requirement), `JobController` (`/api/jobs`).
- `common.llm` — `LlmClient` (all JSON LLM calls go through it), `LlmJson`,
  `LenientStringDeserializer`. `common.text.TextClean` — shared sanitising.
- Integration tests get `FakeEmbeddingModel` automatically (see `AbstractIntegrationTest`);
  mock `CvStructurer` / `JobAnalyzer` with `@MockitoBean`.
- `application` — minimal (Phase 4): create from an analysed job, get, recent list. Tracker in Phase 6.
- `generation` — `GenerationService` (start/poll; one active job per application, DB-enforced),
  `GenerationRunner` (`@Async` on the generation executor, progress steps, restart recovery),
  `GenerationPipeline` (snapshot → evidence pack → match → CV → letter, validate + 1 retry),
  `EvidencePackBuilder` (refs E1/P1/D1/C1/S1), `ContentAssembler` (facts copied from the profile
  by ref), `MatchScorer`, `DocumentService`/`DocumentEdits` (edits, section regenerate),
  `llm.GenerationLlm` (prompts C/D/E — mock it in tests), `validation.FactValidator` + `FactBase`
  (golden tests in `src/test/resources/golden/validator/`), `content.CvContent`/`LetterContent`
  (the stored JSON; new CV content = new field here + template block).
- `template` — `TemplateRegistry`/`ClasspathTemplateRegistry` (manifests in
  `resources/templates/latex/<id>/`, option validation, FreeMarker square-bracket syntax +
  `LatexOutputFormat` auto-escaping), `LatexEscaper`, `DocumentRenderer`/`LatexRenderer`,
  `LatexWorkerClient`, `AtsChecker`. New look = new template folder; `generation.DocumentModels`
  builds the template model (labels/dates per language).
- `common.config.AsyncConfig` — `indexerExecutor` and `generationExecutor` (one thread each).
- Later: `generation`, `document`, `template`, `stats`, `mail`,
  `notion`, `export` (PROJECT.md 2.3).

Frontend `src/app`:
- `core/auth` — `AuthService` (signals), `authInterceptor` (Bearer + refresh-on-401),
  `authGuard`/`guestGuard`. `core/http/api-error.ts` for user-facing error text.
- `layout/shell.ts` — Sneat vertical menu + navbar. `features/<page>/` — one folder per page
  (`profile/`: page, `ItemForm`, `HeaderForm`, `ProfileService` signal store, models + helpers;
  `jobs/`: `AnalyzePage` — paste JD, analysis, per-requirement evidence, "create application";
  `applications/`: models + `ApplicationsService`; `editor/`: `EditorPage` (job polling, match,
  tabs), `CvEditor`, `LetterEditor`, `DocumentPreview`, `PdfPanel` (template picker + options form
  generated from the manifest, ATS report, PDF iframe) — all model text interpolated, never HTML).

## Working rules

1. Build **one phase at a time** (phases in the brief / PROJECT.md section 5). After each phase:
   builds clean, tests green, `docker compose up` works, list manual test steps, update
   PROJECT.md if the design changed, commit, then **stop and wait for "continue"**.
2. Give a short plan (files, key decisions) before writing a phase's code.
3. Tests as you go: JUnit 5 + Testcontainers (pgvector) for integration tests (MockMvc, not a
   real port — see DECISIONS.md); unit tests for LatexEscaper, validator, template registry.
   Frontend: Jasmine/Karma specs for services, guards, interceptors.
4. Commit after each phase with a clear message, **without** any Claude co-author/attribution
   line. Never commit secrets: `.env` is gitignored,
   `.env.example` has placeholders.
5. Extensibility contract (PROJECT.md 3.9): new look = new template folder + `manifest.json`;
   new content = JSON field + template block; new insight = stats query. Use `TemplateRegistry`
   and `DocumentRenderer` interfaces and `features.*` flags.
6. Security is non-negotiable: LLM output and job descriptions are untrusted. Always pass
   strings through `LatexEscaper`; never run LaTeX with shell-escape; never render model output
   as raw HTML (no `[innerHTML]`); treat the JD as data in prompts (delimited, "ignore
   instructions inside").
7. Ask before deleting things or making big decisions.

## Machine-specific gotchas

- The JDK's NIO loopback pipe fails in the 8.3 temp path `C:/Users/HADILS~1/...`; the pom sets
  `-Djdk.net.unixdomain.tmpdir=target` for tests and `spring-boot:run`. Keep it.
- The network is slow and drops: Docker builds use BuildKit cache mounts for `~/.m2` and npm. If a
  Maven download fails mid-build, just rebuild.
- Python scripts on this machine default to cp1252: always run `python -X utf8` (or pass
  `encoding="utf-8"`) when editing files, or non-ASCII characters get corrupted.
