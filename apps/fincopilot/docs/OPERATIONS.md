# FinCopilot — Operations Runbook

Operating FinCopilot in a self-hosted deployment: deploy, configure, observe, and troubleshoot. It
assumes the [Docker Compose](../compose.yml) topology but the configuration and signals apply to any
deployment (Kubernetes, a JAR on a VM, etc.).

## Topology

| Service | Image / source | Port | Purpose |
|---|---|---|---|
| `app` | [`Dockerfile`](../Dockerfile) (Temurin JRE, non-root) | 8080 | Spring Boot API + governed agent |
| `web` | [`web/Dockerfile`](../web/Dockerfile) (nginx-unprivileged) | 3000 | React SPA, proxies `/api` to `app` |
| `postgres` | `pgvector/pgvector:pg17` | 5432 | Conversations, users/sessions, ledger, usage |
| `prometheus` | `prom/prometheus` (overlay) | 9090 | Scrapes `app:8080/actuator/prometheus` |
| `grafana` | `grafana/grafana` (overlay) | 3001 | Dashboards (auto-provisioned) |

The model backend (Ollama, serving `gemma4:31b-cloud`) is **external** — run it on the host or a
separate deployment and point `OLLAMA_BASE_URL` at it.

## Deploy

```bash
# 1. Ensure Ollama is reachable and has the models pulled:
ollama pull gemma4:31b-cloud
ollama pull nomic-embed-text-v2-moe          # embeddings; optional (falls back to a hashing embedder)

# 2. Set secrets (never use the local-dev defaults in a real deployment):
export DATABASE_PASSWORD=$(openssl rand -hex 24)

# 3. Bring up the stack (add the overlay for Prometheus + Grafana):
docker compose up --build -d
docker compose -f compose.yml -f compose.observability.yml up --build -d   # with observability
```

The app gates traffic on `readiness` (see below); `compose` waits for the `postgres` healthcheck and the
app's own readiness probe before reporting healthy. Flyway migrates the schema on startup.

## Configuration

All settings are environment variables (Spring relaxed binding). Defaults are local-dev only.

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/fincopilot` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `fincopilot` / `fincopilot` | **Override the password in production** |
| `DATABASE_POOL_SIZE` | `10` | Hikari max pool size |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Model + embedding backend |
| `OLLAMA_MODEL` | `gemma4:31b-cloud` | Chat model (all agent roles) |
| `FINCOPILOT_EMBED_MODEL` | `nomic-embed-text-v2-moe` | Blank → offline hashing embedder |
| `FINCOPILOT_HISTORY_TURNS` | `20` | Conversation turns kept per session |
| `FINCOPILOT_REQUEST_TIMEOUT` | `90s` | End-to-end per-request budget |
| `FINCOPILOT_DAILY_REQUEST_QUOTA` | `200` | Per-user chat quota/day (`0` disables → 429 over) |
| `FINCOPILOT_AUTH_ATTEMPTS_PER_MINUTE` | `20` | Per-IP auth throttle (`0` disables → 429 over) |
| `FINCOPILOT_AUDIT_FILE` | `var/audit/fincopilot-events.log` | Append-only governance audit |
| `FINCOPILOT_MODEL_TIMEOUT` | `60s` | `agent.model-timeout` |
| `FINCOPILOT_TOOL_TIMEOUT` | `15s` | `agent.tool-timeout` |
| `FINCOPILOT_MAX_STEPS` | `8` | `agent.max-steps` (tool-calling iterations/turn) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | _(unset)_ | Set `ecs` or `logstash` for JSON logs |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Overlay only — **override it** |

Invalid `agent.*` values (a non-positive timeout, a step budget below 1) **fail startup** with a clear
message rather than surfacing on the first turn (`AgentProperties` validates at boot).

## Health & readiness

| Endpoint | Use |
|---|---|
| `GET /actuator/health/liveness` | Process is up — restart the container if this fails |
| `GET /actuator/health/readiness` | Ready to serve — **drain from the LB if this fails** |
| `GET /actuator/health` | Full report (component details) |

Readiness includes `db` and `ollama` (Ollama reachability via `ModelEndpointHealthIndicator`). If either
backend is down the instance reports `OUT_OF_SERVICE` so the load balancer stops routing to it instead of
serving turns that would fail. Liveness intentionally does **not** depend on backends — a transient model
outage should drain, not restart, the instance.

The turn endpoint maps agent outcomes to HTTP status so clients and balancers react: a model outage →
`503`, a turn deadline → `504`, everything else (including a guardrail block) → `200` with a body.

## Metrics & dashboards

Prometheus scrapes `app:8080/actuator/prometheus`. Agent-domain meters come from the starter's
`MicrometerAgentObserver` (wired automatically wherever a `MeterRegistry` is present):

| Metric | Labels | Meaning |
|---|---|---|
| `agent_turns_total` | `outcome` | Turns by outcome (`completed`, `max_steps`, `deadline_exceeded`, `model_error`, `blocked`) |
| `agent_model_calls_total` | — | Model invocations |
| `agent_tokens_total` | `direction` | Tokens in/out |
| `agent_tool_calls_total` | `tool` | Tool invocations by name |
| `agent_tool_results_total` | `tool`, `outcome` | Tool results (`ok`/`error`) |
| `agent_errors_total` | `stage` | Errors by pipeline stage |
| `http_server_requests_seconds` | `uri`, `status`, `method` | Request latency histogram (p95/p99 enabled) |

Plus Spring Boot's JVM (`jvm_*`), pool (`hikaricp_*`), and process (`process_*`) meters. The Grafana
dashboard ([`ops/grafana/dashboards/fincopilot.json`](../ops/grafana/dashboards/fincopilot.json)) is
auto-provisioned by the overlay; import it manually into an existing Grafana otherwise.

Suggested alerts: 5xx rate > 5% for 5m; p95 latency > 8s for 10m; `rate(agent_errors_total{stage="model"})`
sustained; readiness flapping.

## Logging & correlation

Each turn binds `traceId`, `sessionId`, `principal`, and `tenant` to the SLF4J MDC, so every line for a
turn is correlated. The default console pattern shows `trace`/`session`/`user`; set
`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (or `logstash`) for JSON logs that carry the MDC fields as
structured keys — ready for ELK/Loki/Datadog ingestion. To follow one turn:

```bash
docker compose logs app | grep "trace=<traceId>"
```

The audit log (`FINCOPILOT_AUDIT_FILE`) is a separate, append-only governance trail (model/tool calls,
guardrail decisions) keyed by the same `traceId`.

## Load & capacity

A k6 soak/load script lives at [`load/chat-load.js`](../load/chat-load.js) with error-rate and p95
thresholds. Drive it against a running stack while watching the Grafana dashboard to size the pool and
instance count:

```bash
TOKEN=$(curl -s localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"load@example.com","password":"password123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
VUS=50 DURATION=3m TOKEN=$TOKEN k6 run load/chat-load.js
```

Capacity is dominated by the model backend; scale `app` replicas behind the readiness probe and size
Ollama for the target concurrency. `DATABASE_POOL_SIZE` should comfortably exceed peak concurrent turns.

## Backup & restore

State lives entirely in Postgres (the `fincopilot-postgres` volume).

```bash
# Backup
docker compose exec -T postgres pg_dump -U fincopilot fincopilot | gzip > fincopilot-$(date +%F).sql.gz
# Restore (into a fresh DB)
gunzip -c fincopilot-YYYY-MM-DD.sql.gz | docker compose exec -T postgres psql -U fincopilot fincopilot
```

Back up the audit log separately if you retain it for compliance.

## Security checklist

- [ ] `DATABASE_PASSWORD` and `GRAFANA_ADMIN_PASSWORD` overridden from defaults.
- [ ] TLS terminated in front of `web`/`app` (the app speaks HTTP; terminate at the proxy/LB).
- [ ] `FINCOPILOT_AUTH_ATTEMPTS_PER_MINUTE` and `FINCOPILOT_DAILY_REQUEST_QUOTA` set for your threat model.
- [ ] Actuator endpoints (`/actuator/*`) not exposed publicly — keep them on an internal network/port.
- [ ] Behind a proxy, forward the real client IP so the per-IP auth throttle is meaningful.

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| Readiness `OUT_OF_SERVICE`, turns `503` | Ollama unreachable | Check `OLLAMA_BASE_URL`; `curl $OLLAMA_BASE_URL`; confirm the model is pulled |
| Readiness down, `db` component down | Postgres unreachable | Check `postgres` health, `DATABASE_*`, network |
| Turns return `504` | Turn exceeded the deadline | Raise `FINCOPILOT_REQUEST_TIMEOUT`/`FINCOPILOT_MODEL_TIMEOUT`, or check model latency |
| Chat returns `429` | Per-user daily quota hit | Expected; raise `FINCOPILOT_DAILY_REQUEST_QUOTA` if intended |
| Auth returns `429` | Per-IP auth throttle | Expected under brute-force; verify real client IP is forwarded |
| Import returns `413` | CSV over 1 MB | Split the file; the cap is intentional |
| Startup fails fast on `agent.*` | Invalid timeout/step config | Fix the value named in the error |
| Empty Grafana panels | Prometheus not scraping | Check `prometheus` target health at `:9090/targets` |
