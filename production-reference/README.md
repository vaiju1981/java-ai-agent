# Production reference service

This is the supported deployment-shaped path through the library: Spring Boot HTTP, PostgreSQL with
Flyway-owned schema, HikariCP pooling, bounded turns, durable conversation history, durable async
audit, fail-closed JSON-schema validation, deny-by-default effectful tools, graceful shutdown, and
Actuator health/metrics.

Prerequisites: Java 21, Docker, and Ollama with `llama3.2` (or set `OLLAMA_MODEL`).

```bash
docker compose -f production-reference/compose.yml up -d
./gradlew :production-reference:run
```

```bash
curl -s localhost:8080/api/agent/turn \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: acme' \
  -H 'X-Principal-Id: user-42' \
  -H 'Idempotency-Key: request-123' \
  -d '{"sessionId":"support-123","input":"Give me a two-line status update."}'
curl -s localhost:8080/actuator/health/readiness
```

`X-Tenant-Id` and `X-Principal-Id` must be overwritten by a trusted authentication gateway; never
forward client-supplied identity headers unchanged. The example deliberately has no effectful tool.
When adding one, implement `ContextualTool`, use its tenant/principal/idempotency context, and replace
the default deny policy with an application authorization policy.

For multiple replicas, route a session consistently or add distributed per-session coordination.
Concurrent writers fail the complete turn transaction rather than persisting partial history.
