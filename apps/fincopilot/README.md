# FinCopilot

The v0.2.0 flagship application — a **grounded finance copilot** for individuals and small businesses,
built on `java-ai-agent`. See [docs/V0.2.0-PLAN.md](../../docs/V0.2.0-PLAN.md) for the full plan.

> **Status: M0 (walking skeleton), in progress.** Backend chat + Docker stack are in place. Consumer
> auth and the React UI are the next M0 increments; the Analyst (M1) and grounded Advisor (M2) follow.

## What works today

- A governed agent on the **Ollama** substrate (one model, `gemma4:31b-cloud` by default), with crisis
  + PII guardrails and durable conversation persistence (Postgres).
- A session-based chat surface:
  - `POST /api/chat/turn` — synchronous; returns the guarded `AgentResponse`.
  - `POST /api/chat/stream` — Server-Sent Events: `tool` / `tool_result` events, then a single guarded
    `final` event. (Raw model tokens are never streamed — output guardrails run on the final answer.)

## Run it

Prerequisites: Docker, and an [Ollama](https://ollama.com) endpoint reachable from the container with
the configured model available (`gemma4:31b-cloud`; for the `-cloud` tag the Ollama host must be signed
in to Ollama cloud).

```bash
cd apps/fincopilot
docker compose up --build
# then, in another shell:
curl -sN http://localhost:8080/api/chat/stream \
  -H 'X-Principal-Id: demo-user' -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","input":"What can you help me with?"}'
```

The Postgres service uses the `pgvector/pgvector:pg17` image (so the M2 RAG retriever needs no swap).
Ollama runs on the host by default (`OLLAMA_BASE_URL=http://host.docker.internal:11434`).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `OLLAMA_MODEL` | `gemma4:31b-cloud` | the single model for all roles |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local-dev defaults | Postgres connection |

Build just this module: `./gradlew :apps:fincopilot:build`.
