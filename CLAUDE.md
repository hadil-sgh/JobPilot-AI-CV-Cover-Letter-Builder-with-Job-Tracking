# JobPilot

AI-powered CV & motivation letter builder with job tracking. Source of truth for architecture,
schema, API, prompts, and roadmap is [docs/PROJECT.md](docs/PROJECT.md). Deviations from that
doc (and why) are logged in [docs/DECISIONS.md](docs/DECISIONS.md) — check both before assuming
the doc's plan matches what's actually built.

## Stack

- **Backend:** Spring Boot 3.3, **Java 17** (doc specifies 21; this machine only has JDK 17/8 —
  see docs/DECISIONS.md), Spring Security + JWT, Spring Data JPA, Flyway, PostgreSQL 16 + pgvector.
  Spring AI (Ollama/pgvector starters) arrives in Phase 3 when RAG work starts.
- **Frontend:** Angular **15.2.11** (doc specifies 18; this machine's global CLI is v15 — see
  docs/DECISIONS.md), hand-authored standalone components + `bootstrapApplication` (no NgModule),
  functional router guards/HTTP interceptors, RxJS `BehaviorSubject` for state (v15 has no Signals
  API).
- **DB:** PostgreSQL 16 + pgvector, versioned via Flyway (`backend/src/main/resources/db/migration`).
- **LLM (from Phase 3):** Llama 3.1 8B + nomic-embed-text via Ollama.
- **PDF (from Phase 5):** LaTeX compiled in a sandboxed `latex-worker` container.
- **Infra:** Docker Compose, one command (`docker compose up`) starts everything.

## Commands

Backend (`backend/`):
```bash
mvn compile              # compile
mvn test                 # unit + Testcontainers (pgvector) integration tests — needs Docker running
mvn spring-boot:run       # run locally against a local Postgres (see application.yml for defaults)
```

Frontend (`frontend/`):
```bash
npm install
npx ng serve              # dev server on http://localhost:4200
npx ng build --configuration development   # dev build
npx ng build              # production build (used by Dockerfile)
```

Everything:
```bash
docker compose up -d --build
```

## Package layout

Backend (`backend/src/main/java/com/jobpilot/`): `auth` (JWT, users, refresh tokens, security
config), `common` (exceptions). Later phases add `profile`, `job`, `application`, `generation`,
`document`, `template`, `stats`, `mail`, `notion`, `export` per docs/PROJECT.md section 2.3.

Frontend (`frontend/src/app/`): `core/auth` (AuthService, guard, interceptor, token storage),
`features/{login,register,dashboard}`. Later phases add feature folders per page (profile,
job-analysis, editor, tracker, settings).

## Working rules (from the project brief — keep following these)

1. Build **one phase at a time**. After each phase: verify it compiles, tests pass, and
   `docker compose up` works, then stop and wait for "continue" before the next phase.
2. Give a short plan (files, key decisions) before writing a phase's code.
3. Write tests as you go — JUnit 5 + Testcontainers (pgvector) for integration tests, unit tests
   for pure logic (`LatexEscaper`, validator, template registry, once those exist).
4. Commit after each phase with a clear message. Never commit secrets — use `.env` (see
   `.env.example`); `.env` is gitignored.
5. Extensibility contract (section 3.9 of the doc): new CV/letter look = new template folder +
   `manifest.json`; new content = JSON field + template block; new insight = a stats query.
   Use `TemplateRegistry` / `DocumentRenderer` interfaces once template work starts (Phase 5),
   plus feature flags for half-built features.
6. Security is non-negotiable: LLM output and job descriptions are untrusted input. Route all
   such strings through `LatexEscaper` once it exists, never invoke LaTeX with shell-escape,
   never render model output as raw HTML, and always treat the JD as data in prompts (not
   instructions).

## Environment notes specific to this machine

- **Java 21 → 17, Angular 18 → 15**: both are deliberate deviations from docs/PROJECT.md's fixed
  stack, made because this machine doesn't have those versions installed and the user asked to
  work with what's already here rather than install new toolchains. Full reasoning in
  docs/DECISIONS.md. If a future contributor has JDK 21 / Angular 18 available, these can be
  bumped back — nothing in Phase 1 code depends on version-17-only or version-15-only behavior
  except the *absence* of Signals on the frontend and the NgModule-free hand-rolled standalone
  bootstrap.
- **Integration tests run MockMvc, not a real embedded server** (`webEnvironment = MOCK` +
  `@AutoConfigureMockMvc`) — `RANDOM_PORT` reliably fails on this machine with
  `SocketException: Invalid argument: connect` from embedded Tomcat's loopback wakeup pipe,
  most likely due to Docker Desktop's virtual network adapters. See docs/DECISIONS.md.
- Docker Desktop must be running before `mvn test` (Testcontainers) or `docker compose up`.
