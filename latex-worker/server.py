"""
JobPilot latex-worker: a tiny, sandboxed LaTeX compile service (PROJECT.md 3.9).

POST /compile  JSON {"main": "cv.tex", "files": {"cv.tex": "...", "jobpilot.sty": "..."}}
               -> 200 application/pdf, or 422 {"error": ..., "log": <tail>} / 400 / 413 / 503
GET  /health   -> 200 "ok"

Security: Tectonic runs with --untrusted (no shell-escape, no insecure primitives) and
--only-cached (packages were cached at image build; the container has no network anyway).
Only plain-text .tex/.sty/.cls files with safe names are accepted; every compile happens in a
fresh temp dir that is always deleted; one compile at a time per CPU, 20 s timeout.
"""

import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "8090"))
MAX_BODY = 2 * 1024 * 1024
MAX_FILES = 20
TIMEOUT_S = int(os.environ.get("COMPILE_TIMEOUT", "20"))
NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,60}\.(tex|sty|cls)$")
# Defence in depth. Tectonic --untrusted blocks shell-escape but NOT reading absolute paths
# (\input{/etc/passwd} works). Content is LaTeX-escaped by the backend, so only our templates carry
# commands; they never need raw file I/O, catcode tricks or absolute/parent paths, so reject them.
FORBIDDEN = re.compile(
    r"\\(?:openin|openout|read|readline|write18|immediate\s*\\write|catcode|directlua|luaexec"
    r"|input|include|InputIfFileExists|IfFileExists|includegraphics|lstinputlisting|verbatiminput)\s*\{\s*(?:/|\.\.|~)"
    r"|\\(?:openin|openout|write18|catcode|directlua|newread|newwrite)\b"
)
SLOTS = threading.BoundedSemaphore(int(os.environ.get("MAX_CONCURRENT", "1")))


class Handler(BaseHTTPRequestHandler):
    server_version = "jobpilot-latex-worker"
    sys_version = ""

    def log_message(self, fmt, *args):  # concise access log without client data
        print("%s %s" % (self.command, self.path), flush=True)

    def _json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok")
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/compile":
            return self._json(404, {"error": "not found"})
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            return self._json(413, {"error": "request too large or empty"})
        try:
            req = json.loads(self.rfile.read(length).decode("utf-8"))
            main, files = req["main"], req["files"]
        except (ValueError, KeyError, TypeError):
            return self._json(400, {"error": "expected JSON {main, files}"})
        if not isinstance(files, dict) or not 0 < len(files) <= MAX_FILES:
            return self._json(400, {"error": "files must be an object with 1-%d entries" % MAX_FILES})
        if not all(isinstance(k, str) and NAME.match(k) and isinstance(v, str) for k, v in files.items()):
            return self._json(400, {"error": "invalid file name or content"})
        if main not in files or not main.endswith(".tex"):
            return self._json(400, {"error": "main must be one of the .tex files"})
        bad = next((n for n, v in files.items() if FORBIDDEN.search(v)), None)
        if bad:
            return self._json(400, {"error": "forbidden LaTeX command (file I/O or absolute path) in " + bad})

        if not SLOTS.acquire(timeout=30):
            return self._json(503, {"error": "worker busy"})
        workdir = tempfile.mkdtemp(prefix="job-", dir="/tmp")
        try:
            for name, content in files.items():
                with open(os.path.join(workdir, name), "w", encoding="utf-8") as f:
                    f.write(content)
            try:
                proc = subprocess.run(
                    ["tectonic", "--untrusted", "--only-cached", "--keep-logs", "--chatter", "minimal", main],
                    cwd=workdir, capture_output=True, timeout=TIMEOUT_S,
                    env={"PATH": os.environ.get("PATH", ""), "HOME": os.environ.get("HOME", "/tmp"),
                         "XDG_CACHE_HOME": os.environ.get("XDG_CACHE_HOME", ""),
                         "TECTONIC_UNTRUSTED_MODE": "1"},
                )
            except subprocess.TimeoutExpired:
                return self._json(422, {"error": "compilation timed out after %ds" % TIMEOUT_S, "log": ""})
            pdf = os.path.join(workdir, main[:-4] + ".pdf")
            if proc.returncode != 0 or not os.path.exists(pdf):
                log = (proc.stdout + proc.stderr).decode("utf-8", "replace")
                return self._json(422, {"error": "LaTeX compilation failed", "log": log[-4000:]})
            with open(pdf, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "application/pdf")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)
            SLOTS.release()


if __name__ == "__main__":
    print("latex-worker listening on :%d" % PORT, flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
