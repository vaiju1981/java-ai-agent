# Analytics App Plan

This plan turns the analytics demo into an installable analytics application with a React UI, a Java
analytics backend, local-first operation, and optional Ollama summarization through `gemma4:31b-cloud`.

## Product Goal

Build a usable analytics workbench where a user can connect or upload data, inspect the dataset, ask
questions, generate analyses and visualizations, save dashboards, and export evidence-backed results.

The app should feel closer to an early product than a demo:

- installable with Docker or local dev commands;
- usable without cloud credentials;
- deterministic in CI;
- capable of handling small CSVs locally and larger warehouse-style datasets through connector plans;
- transparent about data lineage, SQL, sampling, model usage, and causal limitations.

## App Shape

### Primary Screens

1. Data Sources
   - Upload CSV.
   - Reopen recent local datasets.
   - Show connector cards for local CSV, SQLite, Hugging Face, Kaggle, and BigQuery public datasets.
   - Mark connectors as local, configured, metadata-only, or unavailable.
   - Show row count, file size, inferred schema, and import status.

2. Data Preview
   - Schema table with inferred type, missing count, distinct count, examples, and semantic role.
   - Sample rows with pagination.
   - Data quality panel for missingness, duplicates, outliers, cardinality, and suspicious columns.
   - Filter controls generated from the active dataset, not hard-coded values.

3. Ask Data
   - Chat box for natural-language questions.
   - Tool trace showing which analytics tool ran.
   - Answer panel with tables, chart suggestions, SQL, evidence ids, and caveats.
   - Ollama-only summarization when `OLLAMA_BASE_URL` is configured, using `gemma4:31b-cloud`.
   - Local deterministic fallback when Ollama is not configured.

4. Analysis Workspace
   - Correlation explorer.
   - Feature importance and driver analysis.
   - Segment comparison and pivots.
   - Hypothesis tests.
   - Regression diagnostics and model card.
   - Anomaly detection.
   - Forecasting.
   - Causal sanity checks.
   - Every analysis produces structured results that can be pinned to a dashboard.

5. Charts And Plots
   - Chart recommendation from a selected result.
   - Bar, line, scatter, histogram, box plot, heatmap, and table views.
   - Vega-Lite compatible chart specs for portability.
   - Visual controls for dimensions, metrics, aggregation, filters, and sorting.

6. Dashboard Builder
   - Pin KPI, chart, table, insight, and evidence tiles.
   - Dashboard filters generated from active data.
   - Drilldowns from chart/table rows to supporting records.
   - Save dashboard JSON and renderable HTML.

7. Evidence And Governance
   - Dataset lineage.
   - Tool inputs and outputs.
   - SQL preview and result limits.
   - Sampling and query-cost estimates.
   - Decision log with owner, status, rationale, and linked evidence.
   - Causal and model-risk warnings.

## Architecture

### Frontend

Path:

```text
demos/analytics-ui/
```

Recommended stack:

- React + TypeScript + Vite.
- CSS modules or plain CSS with a small design-token layer.
- TanStack Table for schema/data/result tables.
- Vega-Lite or ECharts for charts.
- No heavy UI framework initially; keep the UX dense and work-focused.

Frontend modules:

```text
demos/analytics-ui/src/
  api/
    analyticsApi.ts
  app/
    App.tsx
    routes.tsx
  components/
    AppShell.tsx
    ConnectorCard.tsx
    DatasetUpload.tsx
    FilterBuilder.tsx
    EvidencePanel.tsx
    ResultTable.tsx
    ChartPanel.tsx
  features/
    data-sources/
    data-preview/
    ask-data/
    analysis-workspace/
    dashboard-builder/
  styles/
    tokens.css
    app.css
```

### Backend

Keep using the Java demos backend first:

- `AnalyticsDashboardServer` becomes the app API server.
- Static React assets can be served by the Java server in packaged mode.
- During development, Vite proxies `/api/*` to the Java server.

Backend API target:

```text
GET  /api/session
GET  /api/connectors
POST /api/upload
POST /api/connect
GET  /api/schema
GET  /api/profile
GET  /api/sample
GET  /api/filter-options
POST /api/filter
POST /api/chat
POST /api/query
POST /api/analysis/correlation
POST /api/analysis/features
POST /api/analysis/segments
POST /api/analysis/hypothesis
POST /api/analysis/regression
POST /api/analysis/anomalies
POST /api/analysis/forecast
POST /api/analysis/causal-check
POST /api/charts/recommend
POST /api/dashboard/pin
GET  /api/dashboard
POST /api/dashboard/filter
GET  /api/evidence
GET  /api/decisions
POST /api/decisions
```

### Data And Runtime

Local mode:

- uploaded CSV persisted to a local workspace directory;
- SQLite database generated per dataset session;
- dashboard/evidence/decision artifacts persisted as JSON/HTML;
- Tablesaw used for dataframe stats and chart preparation.

Configured connector mode:

- connector metadata stored locally;
- public dataset references supported without network in CI;
- warehouse-style connectors use pushdown SQL, sampling, and cost estimates before result fetch.

Ollama mode:

- only `gemma4:31b-cloud`;
- enabled by `OLLAMA_BASE_URL`;
- if not set, chat remains deterministic with local tool summaries;
- live tests skip unless `OLLAMA_BASE_URL` is set.

## Install And Run

### Docker Target

The first installable target should be:

```bash
docker compose up
```

Services:

```text
analytics-app
  Java API server
  React static assets
  local workspace volume

ollama
  optional profile
  uses existing host Ollama by default when possible
```

Volumes:

```text
./workspace:/app/workspace
```

Environment:

```text
ANALYTICS_WORKSPACE=/app/workspace
OLLAMA_BASE_URL=http://host.docker.internal:11434
```

### Local Dev Target

Backend:

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer
```

Frontend:

```bash
cd demos/analytics-ui
npm install
npm run dev
```

Production build:

```bash
cd demos/analytics-ui
npm run build
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.AnalyticsDashboardServer
```

## Milestones

### Milestone A: App Scaffold

- [x] Create React + TypeScript app under `demos/analytics-ui`.
- [x] Add API client and Vite proxy.
- [x] Render app shell, navigation, and current dashboard data.
- [x] Keep Java embedded HTML as fallback until React is served.
- [x] Acceptance: `npm run build` and `./gradlew :demos:check` pass.

### Milestone B: Dataset Upload And Preview

- [x] CSV upload UI.
- [x] Active session state.
- [x] Schema panel.
- [x] Sample rows.
- [x] Data quality summary.
- [x] Data-driven filters.
- [x] Acceptance: upload a CSV and see filter values, schema, sample rows, and quality metrics update.

### Milestone C: Ask Data

- [x] Chat history.
- [x] Question submission.
- [x] Tool trace.
- [x] Structured result rendering.
- [x] Ollama `gemma4:31b-cloud` summary when configured.
- [x] Deterministic fallback when not configured.
- [x] Acceptance: ask feature/correlation/causal/channel questions and see answers plus evidence.

### Milestone D: Analysis Workspace

- [x] Dedicated analysis tabs for profile, correlations, features, segments, regression, anomalies, forecast,
  and causal checks.
- [x] Result tables and charts.
- [x] Pin-to-dashboard actions.
- [x] Acceptance: each analysis can produce a visible result and a dashboard tile candidate.

### Milestone E: Dashboard Builder

- [x] Pinned tiles.
- [x] Dashboard filters.
- [x] Drilldowns.
- [x] Save/reload dashboard artifact.
- [x] Export dashboard JSON/HTML.
- [x] Acceptance: build a dashboard from uploaded data and reload it from persisted artifacts.

### Milestone F: Connectors And Scale

- [x] Connector catalog UI.
- [x] Metadata-only Hugging Face/Kaggle/BigQuery public dataset cards.
- [x] Local connector configuration forms.
- [x] Sampling and query-cost estimate panels.
- [x] Result cache visibility.
- [x] Acceptance: user can inspect connector readiness and run scale-safe plans without raw data in model
  context.

### Milestone G: Packaging

- [x] Serve React build from Java server.
- [x] Add Dockerfile.
- [x] Add docker-compose.
- [x] Add workspace volume.
- [x] Add quickstart docs.
- [x] Acceptance: fresh checkout can run `docker compose up` and open the app.

### Milestone H: Phase 3 Advanced Analytics

- Clustering.
- Richer model families.
- Stronger causal designs where data supports them.
- Model comparison reports.
- More chart types and statistical diagnostics.
- Acceptance: advanced tools are visible in the Analysis Workspace and covered by unit/regression tests.

## Testing Strategy

Backend:

- JUnit tests for every API endpoint.
- Regression tests for analytics calculations.
- Live Ollama test skipped unless `OLLAMA_BASE_URL` is set.

Frontend:

- Unit tests for API adapters and result mappers.
- Component tests for upload, filters, chat, and charts.
- Browser smoke test for upload -> preview -> ask -> chart -> dashboard flow.

Packaging:

- Docker build test.
- Smoke test against the packaged app.

## Definition Of Done

A phase is done only when:

- the UI path is usable end to end;
- backend APIs have tests;
- deterministic tests pass without Ollama;
- Ollama-specific tests are skipped unless configured;
- Docker/local run docs are updated;
- no model other than `gemma4:31b-cloud` is used for live summarization.
