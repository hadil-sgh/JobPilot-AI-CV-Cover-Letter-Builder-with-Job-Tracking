# JobPilot — AI CV & Cover Letter Builder with Job Tracking

Paste a job description and get a **tailored, fact-checked CV and motivation letter**, compiled with
LaTeX into **ATS-friendly PDFs**, then track the application (Notion sync and email sending are on
the roadmap). Everything runs **locally**: the AI is Llama through Ollama, and no data is sent to a
cloud LLM.

> **Status:** Phases 1–5 are done (auth, profile, RAG, generation, LaTeX PDF). Phase 5.1 (FR ⇄ EN
> translation, faster generation) is in progress. The full design is in
> [docs/PROJECT.md](docs/PROJECT.md), and changes to that design are in
> [docs/DECISIONS.md](docs/DECISIONS.md).

---

## Why

Job hunting means rewriting the same CV and letter for every offer, then tracking each application
by hand. Generic AI tools are fast, but they **invent experience you don't have**. JobPilot only
tailors what is already in your profile. Every claim is grounded in your real experience, and any
claim it cannot verify is flagged for you to review.

**Target users:** job seekers and junior engineers applying to many offers, and students or
career-changers who need to tailor a CV quickly. It suits anyone who wants their CV data kept private
(it can be self-hosted).

## Features

| Area | What it does | Status |
|---|---|---|
| **Account** | Register and log in. JWT access tokens plus rotating refresh tokens with reuse detection. | ✅ |
| **Profile** | Upload your CV (PDF/DOCX). The AI turns it into a structured profile (experience, education, projects, skills, languages, certifications) that you can edit. | ✅ |
| **Job analysis** | Paste a job description. The app extracts the title, seniority, must-haves, nice-to-haves, keywords, language and tone. HTML and prompt-injection lines are removed first. | ✅ |
| **RAG evidence** | Your profile is chunked and embedded into pgvector. Each job requirement is matched to evidence from your profile with hybrid search (vector + keywords). | ✅ |
| **Match score** | A 0–100 score with a yes/partial/no verdict per requirement, plus your skill gaps. | ✅ |
| **Generation** | Writes a tailored CV and letter from the evidence only. It runs in the background, and the UI shows its progress. | ✅ |
| **Fact checker** | Checks numbers, technologies, organisations, skills and language against your profile. On a problem it retries once; anything left becomes a review flag. | ✅ |
| **Editor** | Edit the summary, bullets, skills and letter, or regenerate one section. Facts stay locked to your profile. | ✅ |
| **LaTeX PDF** | Compiles the CV and letter in a sandboxed worker with the `ats-classic` template. You can pick template options. | ✅ |
| **ATS check** | Scores the generated PDF: real text layer, standard headings, readable contact details, page count. | ✅ |
| **FR ⇄ EN** | Switch a CV or letter between French and English; the translation is saved as a new version. | 🚧 Phase 5.1 |
| **Tracker** | Applications table and Kanban board, filters, Excel export. | ⏳ Phase 6 |
| **Notion sync** | Two-way sync of applications with your Notion database. | ⏳ Phase 7 |
| **Email** | Send the CV and letter as attachments; keep a log of sent messages. | ⏳ Phase 8 |
| **Polish** | CI/CD, a second template (`modern-compact`), a statistics dashboard. | ⏳ Phase 10 |

## How it works

```
 CV (PDF/DOCX) ──► Tika/PDFBox/POI ──► Llama (JSON) ──► Profile ──► chunks ──► nomic-embed-text ──► pgvector
                                                                                              │
 Job description ──► sanitise ──► Llama: analysis ──► requirements ──► hybrid retrieval ◄─────┘
                                                              │
                                                              ▼
                                        evidence pack (E1, P1, D1, S1 …)
                                                              │
                      ┌────────────────────┬─────────────────┴──────────┐
                      ▼                    ▼                            ▼
                match judge          CV writer (Llama)          letter writer (Llama)
                      │                    │                            │
                      ▼                    ▼                            ▼
                 match score       facts copied from profile + fact checker (retry once)
                                                   │
                                                   ▼
                       editor ──► FreeMarker + LatexEscaper ──► latex-worker (Tectonic) ──► PDF + ATS score
```

- The LLM only writes free text (summary, bullets, letter paragraphs). Names, titles, employers,
  dates and education are **copied from your profile** by reference, so they cannot be invented.
- The job description and the model's output are treated as **untrusted data**:
  - they are wrapped in delimiters inside prompts;
  - every string is escaped before it reaches LaTeX;
  - LaTeX runs without shell-escape in a network-less, read-only container;
  - model output is never rendered as raw HTML.

## Tech stack and tools

| Layer | Tools |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security (JWT, jjwt), Spring Data JPA, Flyway, Maven |
| AI | Spring AI 1.0 + Ollama. `llama3` 8B writes; `llama3.2:3b` is the fast/fallback model; `nomic-embed-text` makes embeddings |
| Data | PostgreSQL 16 + pgvector (HNSW), JSONB for structured documents |
| Documents | Apache Tika, PDFBox, Apache POI (CV import), FreeMarker + Tectonic (LaTeX), PDFBox (ATS check) |
| Frontend | Angular 20 (standalone components, signals), Sneat admin template, nginx |
| Infra | Docker Compose, GitHub Actions CI |
| Tests | JUnit 5, Mockito, Testcontainers (pgvector + latex-worker), golden-file tests for the fact checker, Karma/Jasmine |

## Quick start

Requirements:
- Docker Desktop.
- About 8 GB of free RAM for the 8B model, or set `OLLAMA_FALLBACK_MODEL` so the app can use a smaller one.
- Optionally, a native [Ollama](https://ollama.com) install, which is faster with a GPU.

```bash
cp .env.example .env
```

Put real random values in `.env`, then start the stack:

```bash
docker compose up -d --build
```

Then open http://localhost:4200, create an account, import your CV and paste a job description.

**Using a native Ollama instead of the Docker one:**
1. In `.env`, remove `COMPOSE_PROFILES` and set `OLLAMA_BASE_URL=http://host.docker.internal:11434`.
2. Pull the models:

```bash
ollama pull llama3 && ollama pull llama3.2:3b && ollama pull nomic-embed-text
```

### Configuration (`.env`)

| Variable | Purpose |
|---|---|
| `DB_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_KEY` | Secrets. Never commit `.env`. |
| `OLLAMA_BASE_URL` | Ollama endpoint: the Docker service or the host. |
| `OLLAMA_CHAT_MODEL` | Main writing model (CV, letter, CV import). |
| `OLLAMA_FAST_MODEL` | Optional smaller model for job analysis and match scoring. |
| `OLLAMA_FALLBACK_MODEL` | Optional model used when the main one does not fit in memory. |
| `OLLAMA_EMBEDDING_MODEL` | Embedding model (`nomic-embed-text`). |

## Development

- **Backend:** `cd backend && mvn spring-boot:run`. It needs Postgres on :5432 and the `.env` values.
- **Frontend:** `cd frontend && npm install && npx ng serve`. It proxies `/api` to :8080.
- **Tests:** they need Docker for Testcontainers.

```bash
cd backend && mvn -B verify
```

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless
```

## Project structure

```
backend/        Spring Boot API: auth, profile, rag, job, application, generation, template
frontend/       Angular app (served by nginx in Docker)
latex-worker/   Sandboxed LaTeX compiler (Tectonic, no network, read-only)
docs/           PROJECT.md (design + roadmap), DECISIONS.md (deviations)
.github/        CI workflow
```

## Roadmap

1. Foundation ✅
2. Profile ✅
3. RAG ✅
4. Generation ✅
5. LaTeX PDF ✅
6. Language toggle and speed 🚧
7. Tracker
8. Notion
9. Email
10. Inbox intelligence (optional)
11. Polish: CI/CD, second template, statistics

Ideas for after that are in the backlog in [docs/PROJECT.md](docs/PROJECT.md#living-backlog-add-to-it-as-you-go).

## License

MIT
