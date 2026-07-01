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
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsInsightAgent
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

## AnalyticsInsightAgent

A CSV-to-insights analytics workflow:

- accepts a caller-provided CSV path, or creates a sample marketing CSV when no path is passed;
- loads the CSV into Tablesaw for dataframe metadata and imports it into a SQLite table named `data`
  with inferred numeric/categorical columns;
- exposes read-only tools for schema inspection, Tablesaw-backed dataset profiling, column profiling,
  data quality scans, outlier detection, Tablesaw-backed Pearson/Spearman correlations, interesting
  feature ranking, a metric/dimension catalog, semantic query planning, Tablesaw-backed group-by aggregates, pivots,
  distribution comparisons, hypothesis-test approximations, regression feature screens, anomaly
  detection, simple forecasts, Tablesaw-prepared chart specs, causal sanity checks, guarded SQL, and
  dashboard creation;
- answers questions such as "what features matter?", "which columns correlate with revenue?", and
  "does discount_rate cause revenue?";
- maps natural-language metric questions through the semantic layer before executing governed SQL;
- writes a persisted evidence manifest with dataset lineage, semantic plans, SQL results, and dashboard
  artifacts;
- creates a dashboard JSON object model with filters, layout, KPI/chart/table/insight tiles, and
  drilldown metadata, then renders it to HTML;
- updates the persisted dashboard from a follow-up prompt, including re-rendering HTML and recording
  update evidence;
- writes a persisted analyst decision log that links owner, status, conclusion, rationale, and evidence
  ids;
- refuses to claim causation from observational data while still surfacing measured associations;
- writes dashboard JSON and HTML artifacts from computed tool results;
- runs an eval suite against the same agent and database-backed tools.

See [`ANALYTICS_AGENT_TOOL_CATALOG.md`](ANALYTICS_AGENT_TOOL_CATALOG.md) for the full product-level
tool catalog and roadmap toward larger public datasets, semantic modeling, warehouse pushdown, and
advanced ML/causal workflows.

Pass a CSV path after `--args`:

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsInsightAgent --args="/path/to/upload.csv"
```
