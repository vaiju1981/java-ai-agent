# Production reference service

The supported deployment-shaped path through the library: Spring Boot HTTP, PostgreSQL with
Flyway-owned schema, HikariCP pooling (with leak detection), bounded turns, durable conversation
history, durable async audit, the content-safety guardrail pipeline, fail-closed JSON-schema
validation, deny-by-default effectful tools, API-key auth, per-caller rate limiting, request-size
caps, model-aware readiness, graceful shutdown, and Actuator health/metrics (Prometheus).

Prerequisites: Java 21, Docker, and Ollama with `llama3.2` (or set `OLLAMA_MODEL`).

## Run it

Containerized (app + PostgreSQL), built from the multi-stage `Dockerfile` (non-root, container-sized
JVM):

```bash
docker compose -f production-reference/compose.yml up --build
```

Or run the app on the host against a composed database:

```bash
docker compose -f production-reference/compose.yml up -d postgres
./gradlew :production-reference:run
```

```bash
curl -s localhost:8080/api/agent/turn \
  -H 'Content-Type: application/json' \
  -H 'X-Api-Key: dev-key' \
  -H 'X-Tenant-Id: acme' \
  -H 'X-Principal-Id: user-42' \
  -H 'Idempotency-Key: request-123' \
  -d '{"sessionId":"support-123","input":"Give me a two-line status update."}'
curl -s localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/prometheus | grep agent_
```

The turn outcome is reflected in the HTTP status: `200` for a completion, a step-budget stop, or a
guardrail block (with `blocked:true` in the body); `503` when the model is unreachable; `504` on a
deadline; `409` on a concurrent-write conflict; `400` on invalid input.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local Postgres / `agent` / `agent` | datasource (a startup warning fires if the password is left at `agent`) |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | `http://localhost:11434` / `llama3.2` | model backend |
| `AGENT_GUARD_MODEL` | _(blank)_ | Llama Guard model (e.g. `llama-guard3:1b`); blank runs crisis + PII guardrails only |
| `agent.api-keys` (YAML map) | _(empty)_ | maps each `X-Api-Key` to the tenant it authenticates (key → tenant), so the tenant is bound to the credential, not a client header. Empty leaves `/api` unauthenticated (a startup warning; fails startup under `prod`). Configure as a YAML map — see `application.yml`. |
| `AGENT_RATE_LIMIT_PER_MINUTE` | `120` | per-caller token-bucket limit (`0` disables) |
| `AGENT_MAX_REQUEST_BYTES` | `65536` | reject larger bodies with `413` |

## Security model

- **Authentication** is a baseline: `X-Api-Key` is checked against the configured `agent.api-keys`. A
  real deployment usually terminates auth at a gateway — leave `agent.api-keys` empty only if such a
  gateway is in front (the service logs a loud warning when it is unauthenticated, and refuses to start
  under the `prod` profile).
- **Tenant** is **bound to the API key** (`agent.api-keys` maps key → tenant), not asserted by a client
  header, so `X-Tenant-Id` cannot be spoofed when authentication is on. `X-Tenant-Id` is honored only as
  a fallback when auth is disabled (dev). **Principal** (`X-Principal-Id`) should be set by a trusted
  gateway; never forward client-supplied identity headers unchanged. Tenant isolation in the store is
  only as strong as the identity it is given.
- **Rate limiting** is in-memory and therefore per instance; a multi-replica deployment needs a
  shared limiter (e.g. Redis) for a global limit.
- **Content safety**: crisis detection and PII scrubbing always run; set `AGENT_GUARD_MODEL` to add
  the Llama Guard classifier (the full kidguard pipeline). Guardrails fail closed.

The example deliberately has no effectful tool. When adding one, implement `ContextualTool`, use its
tenant/principal/idempotency context, and replace the default deny policy with an application
authorization policy.

## Operations

- **Readiness** (`/actuator/health/readiness`) includes a model-reachability probe, so a model
  outage drains the instance from a load balancer instead of serving failing turns.
- **Metrics** are exposed at `/actuator/prometheus` (JVM/HTTP plus `agent_turns`, `agent_model_calls`,
  `agent_tokens`, `agent_errors`). Logs carry the request `traceId` for correlation with the audit log.
- For multiple replicas, route a session consistently or add distributed per-session coordination.
  Concurrent writers fail the complete turn transaction (HTTP `409`) rather than persisting partial
  history; an idempotent turn can opt into `JdbcConversationStore.withConflictRetries(...)`.
