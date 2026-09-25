# Decisions Log

Choices made where `docs/PROJECT.md` was silent, ambiguous, or could not be followed on this
machine. Newest phase first.

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
