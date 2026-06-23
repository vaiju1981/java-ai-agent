# FinCopilot

The v0.2.0 flagship application — a **grounded finance copilot** for individuals and small businesses,
built on `java-ai-agent`. See [docs/V0.2.0-PLAN.md](../../docs/V0.2.0-PLAN.md) for the full plan.

> **Status: M0 complete; M1 (Analyst) landing.** Login → streaming chat → `docker compose up`, plus
> per-user transactions, a grounded Analyst, and a dashboard. The grounded Advisor (M2) follows.

## What works today (M0)

- **Consumer auth** — `POST /api/auth/{signup,login,logout}`: BCrypt-hashed accounts + opaque
  server-side sessions. The chat API requires a `Bearer` session token.
- A governed agent on the **Ollama** substrate (one model, `gemma4:31b-cloud` by default), with crisis
  + PII guardrails and durable conversation persistence (Postgres), behind auth:
  - `POST /api/chat/turn` — synchronous; returns the guarded `AgentResponse`.
  - `POST /api/chat/stream` — Server-Sent Events: `tool` / `tool_result` events, then a single guarded
    `final` event. (Raw model tokens are never streamed — output guardrails run on the final answer.)
- **Ledger + Analyst** (M1): per-user accounts & transactions (manual entry + CSV import,
  `/api/accounts` · `/api/transactions`); the Analyst answers grounded finance questions over your own
  data via READ_ONLY tools (spending-by-category, monthly cashflow, summary); `/api/analytics/*` powers
  the dashboard charts.
- A **React SPA** (`web/`) — sign-up/login, a streaming **Chat**, a **Dashboard** (spending-by-category
  and monthly-cashflow charts + summary), and a **Data** view (manual entry + CSV import) — served by nginx.

## Run it

Prerequisites: Docker, and an [Ollama](https://ollama.com) endpoint reachable from the container with
the configured model available (`gemma4:31b-cloud`; for the `-cloud` tag the Ollama host must be signed
in to Ollama cloud).

```bash
cd apps/fincopilot
docker compose up --build
# then open the UI and sign up:
open http://localhost:3000

# ...or drive the API directly (auth required):
TOKEN=$(curl -s http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -sN http://localhost:8080/api/chat/stream \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","input":"What can you help me with?"}'
```

The SPA (`web/`, port 3000) proxies `/api` to the backend; develop it with `cd web && npm install && npm run dev`.

The Postgres service uses the `pgvector/pgvector:pg17` image (so the M2 RAG retriever needs no swap).
Ollama runs on the host by default (`OLLAMA_BASE_URL=http://host.docker.internal:11434`).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `OLLAMA_MODEL` | `gemma4:31b-cloud` | the single model for all roles |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local-dev defaults | Postgres connection |

Build just this module: `./gradlew :apps:fincopilot:build`.
