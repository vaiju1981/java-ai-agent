# syntax=docker/dockerfile:1
# Multi-stage build for the java-ai-agent production reference service.

# --- build stage: compile and assemble the application distribution ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# Cache the Gradle home across builds (BuildKit). installDist comes from the `application` plugin.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :production-reference:installDist

# --- runtime stage: a slim JRE running as a non-root user ---
FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system agent \
    && useradd --system --gid agent --home-dir /app --no-create-home agent
WORKDIR /app
COPY --from=build /src/production-reference/build/install/production-reference/ /app/
# Audit log directory, writable by the runtime user.
RUN mkdir -p /app/var/audit && chown -R agent:agent /app
USER agent
EXPOSE 8080
# Size the heap to the container's memory rather than the host's.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
# The application-plugin start script honors JAVA_OPTS.
ENTRYPOINT ["/app/bin/production-reference"]
