# Sandboxed LaTeX compiler: Tectonic (XeTeX engine) + a stdlib-only Python HTTP server.
# Packages/fonts are cached at build time (warm-up compile); runtime is offline (--only-cached).
FROM alpine:3.21

RUN apk add --no-cache tectonic python3 && adduser -D -u 1000 worker

USER worker
ENV HOME=/home/worker \
    XDG_CACHE_HOME=/home/worker/.cache
WORKDIR /home/worker

# Warm-up: downloads exactly the bundle files the templates use, then proves an offline compile works.
COPY --chown=worker warmup/ /tmp/warmup/
# (Alpine's tectonic has the classic "V1" CLI only: `tectonic file.tex [flags]`.)
# Each font size option (10/11/12pt) needs its own size*.clo file, so warm all three.
RUN cd /tmp/warmup && for s in 10pt 11pt 12pt; do \
        sed "s/\[11pt\]{article}/[$s]{article}/" warmup.tex > w-$s.tex && tectonic w-$s.tex || exit 1; \
    done \
    && rm -f *.pdf && tectonic --only-cached --untrusted w-10pt.tex && tectonic --only-cached --untrusted w-12pt.tex \
    && rm -rf /tmp/warmup

COPY server.py /app/server.py
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8090/health || exit 1
CMD ["python3", "-u", "/app/server.py"]
