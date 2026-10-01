# JobPilot

AI-powered CV and motivation letter builder. Upload your CV once, paste a job description, get
a tailored, fact-checked CV and letter as ATS-friendly PDFs, send it by email, and track
everything (synced to Notion). Runs fully local: Llama via Ollama, no cloud LLM.

> Status: **Phase 1 — Foundation** (auth, schema, app shell). See [docs/PROJECT.md](docs/PROJECT.md)
> for the full design and roadmap.

## Stack
Spring Boot 3.5 (Java 17) · Spring AI · Angular 20 · PostgreSQL 16 + pgvector · Flyway ·
Ollama (llama3.1:8b, nomic-embed-text) · LaTeX · Docker Compose

## Quick start
```bash
cp .env.example .env        # then put real random values in it
docker compose up -d --build
```
The first full start downloads the Ollama models (several GB). To start without Ollama:
`docker compose up -d --build --no-deps db backend frontend`.

Open http://localhost:4200 and create an account.

## Development
- Backend: `cd backend && mvn spring-boot:run` (needs Postgres on :5432 and `JWT_SECRET`)
- Frontend: `cd frontend && npm install && npx ng serve` (proxies `/api` to :8080)
- Tests: `mvn test` (needs Docker for Testcontainers) and
  `npx ng test --watch=false --browsers=ChromeHeadless`

## Project structure
```
backend/       Spring Boot API
frontend/      Angular app (served by nginx in Docker)
latex-worker/  Sandboxed LaTeX compiler (Phase 5)
docs/          PROJECT.md (design), DECISIONS.md (deviations), style reference
```

## License
MIT
