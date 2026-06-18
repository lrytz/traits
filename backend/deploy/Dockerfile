# syntax=docker/dockerfile:1.7

# Eclipse Temurin JRE 25.
FROM eclipse-temurin:25-jre

# wget for the compose healthcheck (probes /api/health); tini reaps zombies and
# forwards SIGTERM so the JVM shutdown hook runs on `docker compose stop`.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget tini \
    && rm -rf /var/lib/apt/lists/*

# Build metadata (passed by deploy.sh via --build-arg). Informational for now.
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
ENV GIT_SHA=${GIT_SHA} \
    BUILD_TIME=${BUILD_TIME} \
    TZ=Europe/Zurich

RUN useradd --system --uid 10001 --shell /usr/sbin/nologin traits

# Pre-create the data dir so the traits-data named volume inherits traits
# ownership on first init (otherwise root-owned and the non-root JVM can't
# write the SQLite file).
RUN mkdir -p /app/data && chown -R traits:traits /app/data

USER traits
WORKDIR /app

COPY --chown=traits:traits traits.jar    /app/traits.jar
# Vite build output, served by tapir's static-files endpoint (TRAITS_STATIC_FILES).
COPY --chown=traits:traits frontend-dist /app/static
# Initial dataset; entrypoint.sh copies it into the data volume on first boot
# only (live edits then persist across redeploys).
COPY --chown=traits:traits seed.sqlite   /app/seed/traits.sqlite
COPY --chown=traits:traits entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
