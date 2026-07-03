# Demos — scenario showcases

These demos are intentionally larger than the `examples` module. They model full application
workflows with governance, observability, replay, evaluation, workspace artifacts, and realistic
domain data. They are deterministic by default, so they run in CI and on a fresh laptop without API
keys, but they exercise the same public seams used with live models.

For analytics, the goal is not a wide pile of partial tools. The roadmap prioritizes 10-20 complete
capabilities with real implementations, tests, structured outputs, visible artifacts, and clear
governance. Tablesaw is the preferred Java dataframe/visualization substrate for local dataframe,
statistics, and chart work, while SQLite/warehouses remain the governed SQL substrate.

Run a demo:

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.IncidentResponseWarRoom
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.ControlledOperationsCopilot
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.analytics.AnalyticsAgentServer --args="t=/path/to/data.csv"
```

## IncidentResponseWarRoom

A production incident workflow:

- plans an incident into a dependency-aware DAG;
- runs independent investigation workers in parallel;
- retrieves runbook and communications guidance from an in-memory RAG corpus;
- synthesizes a mitigation plan through the model seam;
- wraps the whole composed agent with `Trust.govern(...)`;
- writes a durable audit trail and exposes workspace artifacts (`plan.md`, `step-*.txt`).

## ControlledOperationsCopilot

An operations copilot workflow:

- uses a scripted model to request multiple tools in one step;
- creates and seeds a real SQLite operations database;
- runs read-only account, invoice, and policy tools through `DefaultAgent` against that database;
- demonstrates effectful tool denial for a refund operation before it reaches the database;
- demonstrates an explicitly authorized refund path using a transaction and idempotency key;
- records the run and replays it without re-executing tools;
- reports token usage and runs a small eval suite against the same agent shape.

## Analytics agent (`demos/.../analytics`)

A generic, schema-driven analytics agent over DuckDB. Nothing is dataset-specific: point it at any
CSV(s) (or a live SQL database via `SqlSource`) and it profiles the data, infers roles and
relationships, and answers natural-language questions through a small set of read-only tools.

- **Ingestion:** `CsvSource` imports one or more CSVs into DuckDB (multi-table); `SqlSource` proves
  the same stack works over any JDBC database (tested against SQLite).
- **Profiling:** `DatasetProfiler`/`ColumnProfiler` deterministically infer column roles (identifier,
  dimension, measure, date/timestamp, boolean) and `RelationshipDiscovery` finds join keys — including
  2-column composite keys — purely from the data (coverage/cardinality), never from column names.
- **Semantic model + planning:** `SemanticModel` turns the profile into metrics/dimensions/entity
  keys/time columns; `QueryPlanner` (via `JoinTree`, a BFS spanning tree over discovered relationships)
  builds fan-out-safe SQL for single/star/2-fact-chain joins — pre-aggregating shared-key joins so
  fact-to-fact joins never double-count.
- **Data catalog:** `Catalog` persists relationship include/exclude overrides and free-text
  table/column descriptions as JSON, applied on top of auto-discovery and folded into the schema the
  agent sees — exposed live via `/api/catalog/*` on `AnalyticsAgentServer` (edits take effect
  immediately, no restart).
- **Agent + tools:** `AnalyticsAgent` wires a config-selected `ModelPort` (see `AnalyticsModels`) to
  generic read-only tools — `describe_dataset`, `run_query`, `summarize`, `outliers`, `regression`,
  `correlate`, `run_sql` (a guarded, single-SELECT DuckDB escape hatch for anything the structured
  tools can't express). `AnalyticsSupervisor` optionally routes between an analyst and a
  schema-explainer specialist.
- **Server:** `AnalyticsAgentServer` is a minimal HTTP app (chat UI + `/api/ask`, `/api/profile`,
  `/api/model`, `/api/catalog/*`) with per-session memory.

Run it:

```bash
ANALYTICS_MODEL_PROVIDER=ollama ANALYTICS_MODEL=gemma4:31b-cloud \
  ./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.analytics.AnalyticsAgentServer \
  --args="orders=/path/to/orders.csv customers=/path/to/customers.csv"
```

The server prints a local URL. Table names are inferred from the file name if you pass bare CSV paths
instead of `name=path` pairs. Without `ANALYTICS_MODEL_PROVIDER` set, `/api/ask` is disabled but
`/api/profile`/`/api/model` still serve the deterministic profiler output (no LLM needed).

Relevant env vars: `ANALYTICS_PORT` (default: random free port), `ANALYTICS_DB` (DuckDB file, default
`workspace/analytics.duckdb`), `ANALYTICS_CATALOG` (catalog JSON file, default
`workspace/catalog.json`), `OLLAMA_BASE_URL` (default `http://localhost:11434`).

Run deterministic demo tests:

```bash
./gradlew :demos:check
```

Live model tests are opt-in and skipped unless `OLLAMA_BASE_URL` is set (they call Ollama with
`gemma4:31b-cloud`):

```bash
OLLAMA_BASE_URL=http://localhost:11434 ./gradlew :demos:test --tests '*AnalyticsAgentLiveTest'
```

The manual real-data acceptance test is gated behind `-PrunManual` plus `ANALYTICS_REAL_DATA_DIR`
(a directory of large CSVs) — it is never part of `:demos:check` or CI:

```bash
ANALYTICS_REAL_DATA_DIR=/path/to/large-csvs \
  ./gradlew :demos:test --tests '*RealDataAcceptanceTest' -PrunManual
```

### Known gaps / remaining work

Composite-key discovery, N-table fan-out-safe planning, and the live data catalog shipped in
[PR #103](https://github.com/vaiju1981/java-ai-agent/pull/103). Deliberately out of scope there,
still open:

- **Tool gap audit** — no forecast tool, no pivot/crosstab, no saved views, no export. The current
  tool set (`run_query`/`summarize`/`outliers`/`regression`/`correlate`/`run_sql`) has been
  user-validated end-to-end on real data, but hasn't had a systematic "what's missing" pass.
- **UI** — chat + plots/charts, and a UI for editing catalog relationships/descriptions (the
  `/api/catalog/*` endpoints exist specifically so a future UI can drive this) is not built. Deferred
  pending the tool/data layer being solid first.
- **Fact chains are capped at exactly 2 fact tables** (documented ceiling, not a bug) — chains of 3+
  fact tables need a generalized grain-union algorithm that wasn't built to avoid shipping an untested
  join strategy. `QueryPlanner` throws a clear error rather than attempting one.
- **Composite keys are capped at 2 columns** (`RelationshipDiscovery.MAX_COMPOSITE_KEY_SIZE`) —
  arbitrary N-column keys need the pairing search generalized past "one seed + one extra column."
- **No `GraphSource`** (Neo4j) — the profiler/planner emit SQL strings, so a graph source needs an
  engine-neutral aggregate capability plus Cypher-native profiling; not a quick adapter.
- **Non-ISO text dates** aren't parsed — `SemanticModel.TimeEncoding.TEXT` only handles ISO-parseable
  strings via `try_cast`; other formats need `strptime` format detection.
- **Latency**: no streaming yet; the biggest lever after model choice.
- **`AnalyticsAgentServer`'s live-rebuild has a documented, accepted atomicity gap** for a single-user
  demo (see the `ponytail:` comment above its volatile fields) — a request landing mid-catalog-edit
  could theoretically see one updated field alongside one stale field. Not a real risk today; the
  upgrade path (one immutable snapshot record behind a single volatile) is noted in code if it ever
  matters.
