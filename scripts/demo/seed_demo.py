"""Seeds a local JobPilot with a fictional demo user, profile, job and generated documents.

Used for the README screenshots and for trying the app quickly. Local stack only.

    python -X utf8 scripts/demo/seed_demo.py [--base http://localhost:4200] [--no-generate]

Demo login (local test account): demo@jobpilot.test / jobpilot-demo-2026
Generation runs the real local LLM: several minutes on a small GPU.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request

EMAIL = "demo@jobpilot.test"
PASSWORD = "jobpilot-demo-2026"
NAME = "Sam Rivera"

PROFILE = {
    "fullName": NAME,
    "headline": "Backend Java Developer",
    "summary": "Backend developer with 3 years of experience building Spring Boot REST APIs, "
               "event-driven services and PostgreSQL data models for fintech products.",
    "phone": "+33 6 12 34 56 78",
    "location": "Lyon, France",
    "links": [{"label": "GitHub", "url": "https://github.com/sam-rivera-demo"},
              {"label": "LinkedIn", "url": "https://www.linkedin.com/in/sam-rivera-demo"}],
    "languages": [{"name": "English", "level": "Fluent (C1)"}, {"name": "French", "level": "Native"},
                  {"name": "Spanish", "level": "Intermediate (B1)"}],
}

ITEMS = [
    {"type": "EXPERIENCE", "title": "Backend Developer", "organization": "Lumen Payments",
     "startDate": "2023-03-01", "description": "Payments platform team (12 engineers).",
     "bullets": ["Built Spring Boot REST APIs for card payments used by 40 merchant partners",
                 "Cut settlement batch time from 3 hours to 25 minutes with Kafka-based processing",
                 "Designed PostgreSQL schemas and Flyway migrations for the ledger service",
                 "Added Testcontainers integration tests, raising coverage from 55% to 82%",
                 "Ran the services on Docker and Kubernetes with GitHub Actions pipelines"],
     "tags": ["Java", "Spring Boot", "Kafka", "PostgreSQL", "Docker", "Kubernetes"]},
    {"type": "EXPERIENCE", "title": "Software Engineering Intern", "organization": "Orbis Analytics",
     "startDate": "2022-02-01", "endDate": "2022-08-31",
     "bullets": ["Developed an Angular dashboard for real-time sales analytics",
                 "Wrote Java REST endpoints and SQL reports for 5 internal teams",
                 "Automated weekly data exports with Python scripts, saving 6 hours per week"],
     "tags": ["Angular", "TypeScript", "Java", "SQL", "Python"]},
    {"type": "PROJECT", "title": "JobPilot", "organization": "Personal project",
     "description": "Local AI assistant that tailors CVs and cover letters to job offers.",
     "bullets": ["RAG over a candidate profile with pgvector and Ollama",
                 "Sandboxed LaTeX rendering to ATS-friendly PDFs"],
     "tags": ["Spring AI", "pgvector", "Angular", "LaTeX"]},
    {"type": "PROJECT", "title": "Expense Splitter API", "organization": "Open source",
     "description": "REST API to share group expenses, with JWT authentication.",
     "bullets": ["Implemented JWT auth and role-based access with Spring Security"],
     "tags": ["Spring Security", "JWT", "Redis"]},
    {"type": "EDUCATION", "title": "MSc in Software Engineering", "organization": "Université de Lyon",
     "startDate": "2020-09-01", "endDate": "2022-09-30"},
    {"type": "EDUCATION", "title": "BSc in Computer Science", "organization": "Université de Lyon",
     "startDate": "2017-09-01", "endDate": "2020-06-30"},
    {"type": "CERTIFICATION", "title": "Oracle Certified Professional: Java SE 17 Developer",
     "organization": "Oracle", "startDate": "2023-11-01"},
    {"type": "SKILL", "title": "Backend", "tags": ["Java", "Spring Boot", "Spring Security", "Hibernate", "REST", "Kafka"]},
    {"type": "SKILL", "title": "Data", "tags": ["PostgreSQL", "Redis", "SQL", "Flyway"]},
    {"type": "SKILL", "title": "DevOps", "tags": ["Docker", "Kubernetes", "GitHub Actions", "Git"]},
    {"type": "SKILL", "title": "Frontend", "tags": ["Angular", "TypeScript"]},
]

JOB = """Backend Java Developer - Nimbus Pay (Paris, hybrid)

Nimbus Pay builds instant payment infrastructure for European banks. We are looking for a
Backend Java Developer to join our Core Payments team.

What you will do
- Design and build Spring Boot microservices for real-time payments
- Own REST APIs used by our banking partners
- Work with Kafka event streams and PostgreSQL
- Improve test automation and our CI/CD pipelines

Requirements
- 2+ years of experience with Java and Spring Boot
- Solid knowledge of REST API design and SQL databases (PostgreSQL)
- Experience with Kafka or another message broker
- Docker; Kubernetes is a plus
- Fluent English; French is a plus

Nice to have
- Experience in payments or fintech
- AWS or another cloud provider
- Angular or another frontend framework
"""


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method, path, body=None, ok=(200, 201, 202, 204)):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(req, timeout=900) as res:
                raw = res.read()
                return res.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            if e.code in ok:
                return e.code, None
            return e.code, e.read().decode(errors="replace")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:4200")
    ap.add_argument("--no-generate", action="store_true")
    args = ap.parse_args()
    api = Api(args.base)

    status, res = api.call("POST", "/api/auth/register", {"email": EMAIL, "password": PASSWORD, "fullName": NAME})
    if status != 201 and status != 200:
        status, res = api.call("POST", "/api/auth/login", {"email": EMAIL, "password": PASSWORD})
        if status != 200:
            sys.exit(f"Login failed ({status}): {res}")
        print("Demo user exists: logged in")
    api.token = res["accessToken"]

    _, profile = api.call("GET", "/api/profile")
    if profile and profile.get("items"):
        print("Profile already seeded")
    else:
        status, res = api.call("PUT", "/api/profile", PROFILE)
        assert status == 200, res
        for item in ITEMS:
            status, res = api.call("POST", "/api/profile/items", item)
            assert status == 201, res
        api.call("POST", "/api/profile/reindex")
        print(f"Profile seeded with {len(ITEMS)} items")

    print("Analysing the job description (fast model)...")
    t = time.time()
    status, job = api.call("POST", "/api/jobs/analyze", {"text": JOB})
    assert status == 200, job
    print(f"  done in {time.time() - t:.0f}s: {job['analysis']['title']} ({job['language']})")
    status, app = api.call("POST", "/api/applications", {"jobId": job["id"], "company": "Nimbus Pay",
                                                          "roleTitle": "Backend Java Developer",
                                                          "country": "France", "workMode": "HYBRID"})
    assert status == 201, app
    print("Application:", app["id"])
    if args.no_generate:
        return

    status, gen = api.call("POST", f"/api/applications/{app['id']}/generate")
    assert status == 202, gen
    t = time.time()
    while True:
        time.sleep(10)
        _, gen = api.call("GET", f"/api/generation-jobs/{gen['id']}")
        print(f"  {time.time() - t:4.0f}s {gen['status']} {gen.get('step') or ''}", flush=True)
        if gen["status"] in ("DONE", "FAILED"):
            break
    if gen["status"] == "FAILED":
        sys.exit("Generation failed: " + str(gen.get("error")))
    for doc_id in (gen["cvDocumentId"], gen["letterDocumentId"]):
        status, doc = api.call("POST", f"/api/documents/{doc_id}/render", {"template": "ats-classic", "options": {}})
        print(f"Rendered {doc_id}: ATS {doc['atsScore'] if status == 200 else doc}")
    print("Open:", f"{args.base}/applications/{app['id']}")


if __name__ == "__main__":
    main()
