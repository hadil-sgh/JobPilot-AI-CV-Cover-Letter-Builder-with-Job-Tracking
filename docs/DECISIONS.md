# Decisions Log

Choices made where `docs/PROJECT.md` was silent, ambiguous, or could not be followed on this
machine. Newest phase first.

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
