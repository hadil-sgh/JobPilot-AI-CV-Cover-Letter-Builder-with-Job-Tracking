# Decisions Log

Records choices made where `docs/PROJECT.md` was silent or ambiguous. Newest first.

## Phase 1

- **Refresh token storage.** The schema in PROJECT.md section 2.4 has no table for refresh
  tokens even though FR-1 requires them. Added `refresh_tokens` (id, user_id, token_hash,
  expires_at, revoked, created_at) in `V2__add_refresh_tokens.sql`. Tokens are hashed
  (SHA-256) before storage so a DB leak doesn't leak usable tokens. Rotation: each
  `/api/auth/refresh` call revokes the old token and issues a new one.
- **JWT library.** `io.jsonwebtoken:jjwt` (jjwt-api/impl/jackson) — not specified in the doc,
  it's the de-facto standard for Spring Boot JWT and keeps token creation/parsing simple.
- **Access/refresh token lifetimes.** Access token: 15 minutes. Refresh token: 7 days.
  Not specified in the doc; short-lived access token limits exposure if leaked, refresh
  token lifetime keeps users from re-logging in constantly during daily job-hunting use.
- **Health check.** Used Spring Boot Actuator's `/actuator/health` instead of a hand-rolled
  endpoint — standard, zero-maintenance, and exposes readiness (DB connectivity) for free.
- **Java version: 17, not 21.** PROJECT.md fixes the stack at Java 21, but the dev machine only
  has JDK 17/8 (no 21) and, per explicit user instruction, we work with the existing local
  toolchain rather than installing a new JDK or building exclusively through Docker. Spring Boot
  3.3 fully supports Java 17, so this only changes the compiler/runtime target
  (`pom.xml` `java.version=17`, both stages of `backend/Dockerfile` on `*-temurin-17`), not the
  framework or any dependency versions. If a real need for Java 21 language features shows up
  later, revisit by installing a JDK 21 (e.g. `choco install temurin21`) — a system change worth
  a separate confirmation when it's actually needed.
- **Angular version: 15, not 18.** PROJECT.md fixes Angular 18 (standalone components + signals),
  but per explicit user instruction we work only with what's already installed: global
  `@angular/cli` is v15.2.11. An `npx @angular/cli@18` scaffold was tried first and fully
  downloaded successfully, but was deleted and redone on v15 once the user confirmed that
  choice (see chat: "no work with the existing versions"). Real consequence: **Angular 15 has
  no signals API at all** (introduced in v16, stabilized in v17) — state management uses plain
  services/RxJS instead. Standalone bootstrapping also isn't exposed as an `ng new` flag on this
  CLI version (`ng new --standalone` errors with "Unknown argument"), so the app is scaffolded
  NgModule-based and later hand-converted to a standalone bootstrap (`bootstrapApplication` in
  `main.ts`) where practical, without relying on CLI schematics for it.
- **CORS / dev ports.** Backend on 8080, Angular dev server on 4200 in local dev (`ng serve`
  proxies or CORS-allows 4200); in Docker Compose, nginx serves the built frontend on 4200
  and talks to `backend:8080` — matches the doc's compose file exactly.
- **Frontend Docker build tuned for this network.** `frontend/Dockerfile` uses `node:20-alpine`
  (much smaller pull than `node:20`), `frontend/.dockerignore` excludes `node_modules`/`dist`
  (build context dropped from ~473MB to ~8KB), and `npm ci` runs with higher fetch retries and
  `--maxsockets=3` because default settings hit repeated `ECONNRESET` inside Docker Desktop.
  Output path is `dist/frontend` (Angular 15 has no `/browser` subfolder, unlike the doc's 18).
- **Verifying without Ollama.** `backend` depends on `ollama` in compose, which pulls a multi-GB
  image that Phase 1 doesn't need. Verification ran db, backend and frontend as separate
  containers on one user-defined network (aliases `db` and `backend`). After repeated Docker
  Desktop restarts the compose network's state went stale (network showed no containers, DNS for
  `db` failed); recreating the network fixed it.
- **Integration tests use MOCK web environment, not RANDOM_PORT.** On this Windows dev machine,
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` reliably failed embedded Tomcat startup with
  `SocketException: Invalid argument: connect` while establishing its internal NIO loopback
  wakeup pipe — a known class of Windows issue triggered by Docker Desktop's virtual network
  adapters (Hyper-V/WSL) confusing the JVM's loopback socket resolution. Forcing
  `-Djava.net.preferIPv4Stack=true` (kept in `pom.xml` surefire config) did not fix it. Switched
  `AbstractIntegrationTest` to `webEnvironment = MOCK` + `@AutoConfigureMockMvc` and rewrote
  `AuthIntegrationTest` on `MockMvc` instead of `TestRestTemplate` — no real server socket is
  opened, so the bug never triggers, and it's the more idiomatic way to test controllers anyway.
- **CORS / dev ports.** Backend on 8080, Angular dev server on 4200 in local dev (`ng serve`
  proxies or CORS-allows 4200); in Docker Compose, nginx serves the built frontend on 4200
  and talks to `backend:8080` — matches the doc's compose file exactly.
