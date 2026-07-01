# Analytics Agent Tool Catalog

This is the target shape for a serious analytics demo: not a CSV chatbot, but the core of an
analytics product that can compete with BI, notebook, and augmented analytics workflows.

The current `AnalyticsInsightAgent` implements the local/CI-safe core: local CSV connector -> Tablesaw
dataframe profiling/correlation/group-by/chart prep + SQLite storage -> metric/dimension catalog and
semantic query planning -> schema/quality/outlier/pivot/distribution/statistical/regression/anomaly/
forecast/dashboard tools -> guarded SQL -> causal caveat. The remaining milestones are deeper
Tablesaw-backed statistics, warehouse-scale connectors, richer ML/causal methods, richer dashboard
interactivity, and a polished dashboard UI.

## Product Bar

The demo should prove that an agent can:

- connect to real files and databases;
- build a semantic understanding of tables, joins, metrics, dimensions, and business definitions;
- run governed SQL and statistical tools instead of guessing;
- distinguish correlation, prediction, and causal evidence;
- create dashboards and narratives from computed results;
- preserve lineage, auditability, cost controls, and reproducibility;
- scale from a small uploaded CSV to multi-million-row public datasets.

## Execution Principle

Build fewer tools, but make each one product-grade. The target is 10-20 complete capabilities with
real backing code, tests, docs, and visible artifacts, not 100 thin wrappers.

Each promoted tool should have:

- a real implementation against a dataframe, SQL engine, model, or artifact store;
- deterministic tests with local data;
- clear governance boundaries: read-only vs effectful, row limits, timeouts, auditability;
- structured JSON output that can back a UI, chart, dashboard, or report;
- an explanation of assumptions and limits, especially for statistics and causal claims.

For the Java analytics engine, prefer using [Tablesaw](https://github.com/jtablesaw/tablesaw) where it
gives us a stronger, more maintainable implementation than hand-rolled code. Tablesaw is a Java
dataframe and visualization library that supports loading, cleaning, transforming, filtering,
summarizing, descriptive statistics, and Plotly-backed visualization. The current SQLite implementation
is a good governed query substrate; Tablesaw is the better fit for dataframe/statistical/chart work.

Recommended split:

- SQLite or a warehouse executes governed SQL and large scans.
- Tablesaw owns local dataframe transformations, profiling, statistics, joins, and chart data prep.
- The agent orchestrates tools, validates intent, creates narratives, and records lineage.

## Tool Catalog

This catalog is the full product vocabulary. The first production-quality implementation should select
the best 10-20 from this list and finish them deeply.

### Priority Complete Tool Set

These are the first-class tools to finish before expanding the surface area:

| Tool | Why it matters | Implementation direction |
|---|---|---|
| `connect_dataset` | One entry point for CSV/local file/JDBC/external refs. | Connector seam + local CSV now; later Hugging Face/Kaggle/warehouse. |
| `infer_schema` | Every downstream tool needs reliable types and field metadata. | Tablesaw column types + SQL metadata. |
| `dataset_profile` | First thing every analyst needs. | Tablesaw-backed summary with types, missingness, numeric stats, top values. |
| `data_quality_scan` | Makes the demo feel production-grade. | Missingness, duplicates, type issues, outliers, PII hints. |
| `semantic_query_plan` | Natural language must become a governed plan, not raw SQL guesses. | Implemented deterministic plan with metrics/dimensions/grain/SQL for the demo dataset. |
| `sql_validate_execute` | Core BI path. | Read-only SQL, allow-list, limits, timeout, result metadata. |
| `groupby_aggregate` | Bread-and-butter analytics. | Tablesaw-backed for local frame; SQL pushdown later for large data. |
| `correlation_analysis` | User explicitly wants correlation. | Tablesaw-backed Pearson/Spearman with caveats and missingness policy. |
| `interesting_features` | Differentiator for augmented analytics. | Correlation, categorical lift, mutual information later. |
| `hypothesis_test` | Separates serious analytics from charting. | t/proportion/chi-square with assumptions and effect size. |
| `regression_fit` | Predictive/statistical bridge. | Start with linear/logistic baseline; report diagnostics. |
| `causal_sanity_check` | Prevents irresponsible claims. | Explicit causal evidence gate and design recommendations. |
| `anomaly_detection` | Operational analytics and monitoring path. | Robust z/MAD/change point later. |
| `forecast_timeseries` | Executive/dashboard use case. | Baseline trend/seasonality first; richer models later. |
| `chart_spec_generate` | UI/dashboard bridge. | Tablesaw-prepared data with Vega-Lite-compatible JSON. |
| `dashboard_create` | Visible artifact that proves value. | Dashboard JSON object model plus rendered HTML now; interactive app later. |
| `dashboard_update` | Makes dashboards iterative instead of one-shot artifacts. | Effectful follow-up tool adds/removes/renames tiles, re-renders HTML, and records update evidence. |
| `insight_brief_generate` | The agent's narrative layer. | Evidence-cited Markdown/HTML report. |
| `audit_lineage_record` | Trust and reproducibility. | Persisted evidence manifest now records dataset, semantic plan, governed SQL, and dashboard artifact events. |

### 1. Data Connection And Ingestion

| Tool | Purpose | Notes |
|---|---|---|
| `connect_csv` | Load a caller-provided CSV with delimiter/encoding/header options. | Current slice exists in simplified form. |
| `connect_excel` | Load `.xlsx` sheets and named ranges. | Useful for analyst workflows. |
| `connect_parquet` | Load Parquet files with schema preservation. | Needed for large public datasets. |
| `connect_jdbc` | Connect to Postgres/MySQL/SQLite/H2 by JDBC. | Demo can start with SQLite, then Postgres. |
| `connect_warehouse` | Connect to BigQuery/Snowflake/Databricks/Redshift. | Use adapter boundary; credentials external. |
| `connect_huggingface_dataset` | Pull a dataset by repo/subset/split. | Good for repeatable public demos. |
| `connect_kaggle_dataset` | Pull a Kaggle dataset/competition artifact. | Requires Kaggle token; keep optional. |
| `sample_dataset` | Produce a deterministic local sample. | Keeps CI green without network. |
| `infer_schema` | Infer column names, types, nullability, units, temporal fields. | Must handle messy names and mixed types. |
| `persist_dataset` | Store loaded data in SQLite/DuckDB/Postgres. | Source of truth for tools. |
| `record_lineage` | Track source file, import time, row count, hashes, transformations. | Needed for reproducibility. |

### 2. Data Profiling And Quality

| Tool | Purpose |
|---|---|
| `dataset_profile` | Row count, column count, inferred types, missingness, min/max/mean, cardinality. |
| `column_profile` | Deep stats for one column: quantiles, histogram, top values, examples. |
| `data_quality_scan` | Missingness, duplicates, invalid values, impossible values, drift candidates. |
| `outlier_scan` | IQR/z-score/MAD outliers by column and segment. |
| `freshness_check` | Validate recency and gaps for time-based datasets. |
| `join_key_discovery` | Suggest primary keys, foreign keys, and likely joins across tables. |
| `grain_detection` | Infer table grain: order line, customer-day, store-week, etc. |
| `pii_scan` | Detect PII columns and apply governance before model exposure. |

### 3. Semantic Layer

| Tool | Purpose |
|---|---|
| `define_metric` | Register metric formulas like revenue, margin, conversion_rate. |
| `define_dimension` | Register business dimensions and allowed values. |
| `metric_catalog` | List canonical metrics, synonyms, owners, descriptions, formulas. |
| `semantic_query_plan` | Convert a natural-language question into metrics, dimensions, filters, time grain. |
| `validate_metric_query` | Check that requested metrics/dimensions are compatible with table grain. |
| `explain_metric` | Explain how a metric is computed and where it came from. |

### 4. Governed Query Execution

| Tool | Purpose |
|---|---|
| `sql_generate` | Generate SQL from the semantic query plan. |
| `sql_validate` | Enforce read-only SQL, table allow-list, row limits, timeout, no DDL/DML. |
| `sql_execute` | Execute approved SQL and return typed rows plus metadata. |
| `query_cost_estimate` | Estimate scanned bytes/rows and reject expensive queries. |
| `query_cache_lookup` | Reuse previous result sets when inputs match. |
| `result_summarize` | Summarize large query results without dumping all rows to the model. |
| `result_sample` | Return stratified/head/tail/random samples for inspection. |

### 5. Descriptive Analytics

| Tool | Purpose |
|---|---|
| `groupby_aggregate` | Compute metrics by dimensions with filters and sorting. |
| `pivot_table` | Build cross-tab summaries. |
| `cohort_analysis` | Retention, repeat behavior, first-seen cohort metrics. |
| `funnel_analysis` | Step conversion, drop-off, segment comparison. |
| `distribution_compare` | Compare distributions across segments. |
| `contribution_analysis` | Decompose change in a metric into segment-level contributions. |
| `waterfall_decomposition` | Build a bridge from previous period to current period. |

### 6. Statistical Analysis

| Tool | Purpose |
|---|---|
| `correlation_analysis` | Pearson/Spearman/Kendall for numeric columns. |
| `association_analysis` | Cramer's V, mutual information, chi-square for categorical pairs. |
| `hypothesis_test` | t-test, Mann-Whitney, chi-square, proportion test with assumptions. |
| `confidence_interval` | Mean/proportion/rate intervals. |
| `regression_fit` | Linear/logistic regression with coefficients, p-values, residual checks. |
| `multicollinearity_check` | VIF/correlation clusters for regression features. |
| `effect_size` | Cohen's d, odds ratio, risk ratio, lift. |
| `multiple_testing_control` | FDR/Bonferroni when scanning many features. |

### 7. Causal And Experiment Analytics

| Tool | Purpose |
|---|---|
| `causal_sanity_check` | State whether data supports causal claims and what assumptions are missing. |
| `ab_test_analyze` | Difference in means/proportions, power, confidence intervals, guardrails. |
| `propensity_score_match` | Estimate observational treatment effect with balance diagnostics. |
| `difference_in_differences` | Pre/post treatment-control analysis. |
| `interrupted_time_series` | Estimate impact of launch/policy change over time. |
| `uplift_model` | Identify segments with high incremental response. |
| `sensitivity_analysis` | Show how unobserved confounding could change conclusions. |
| `causal_dag_register` | Capture user-supplied causal assumptions before estimating effects. |

### 8. Predictive Modeling And ML

| Tool | Purpose |
|---|---|
| `train_baseline_model` | Fit simple regression/classification baseline with train/test split. |
| `forecast_timeseries` | Seasonal naive/ETS/ARIMA/Prophet-style forecasting boundary. |
| `anomaly_detection` | Detect metric spikes/drops and row-level anomalies. |
| `clustering` | Segment entities with k-means/hierarchical clustering. |
| `feature_importance` | Permutation importance/model coefficients. |
| `explain_prediction` | SHAP-like or coefficient-based explanations. |
| `model_eval_report` | RMSE/MAE/R2/AUC/F1/calibration depending on task. |
| `what_if_simulation` | Estimate predictions under changed inputs. |
| `model_card_generate` | Document training data, target, metrics, risks, and intended use. |

### 9. Time Series And Operational Analytics

| Tool | Purpose |
|---|---|
| `time_grain_detect` | Infer daily/weekly/monthly grain and gaps. |
| `seasonality_decompose` | Trend/seasonality/residual components. |
| `period_compare` | WoW/MoM/YoY deltas with contribution analysis. |
| `change_point_detect` | Identify structural breaks. |
| `metric_monitor` | Define alert thresholds and anomaly monitors. |
| `root_cause_timeseries` | Search dimensions explaining a metric movement. |

### 10. Visualization And Dashboarding

| Tool | Purpose |
|---|---|
| `chart_recommend` | Pick chart type based on fields/task/result shape. |
| `chart_spec_generate` | Emit Vega-Lite/ECharts/Plotly spec with data binding. |
| `dashboard_create` | Create a dashboard with sections, filters, charts, and KPI tiles. |
| `dashboard_update` | Add/remove/edit tiles from follow-up prompts. |
| `dashboard_filter_apply` | Apply global filters and cross-filters. |
| `drilldown` | Go from KPI -> segment -> rows. |
| `export_report` | Export Markdown/HTML/PDF/PPT-style narrative. |
| `schedule_report` | Schedule recurring refresh/report delivery. |

### 11. Narrative And Collaboration

| Tool | Purpose |
|---|---|
| `insight_brief_generate` | Executive summary with findings, caveats, next actions. |
| `analysis_plan_create` | Convert broad user question into an analysis plan. |
| `question_clarify` | Ask for target metric, time range, segment, or causal assumption when needed. |
| `citation_attach` | Attach query IDs, chart IDs, metric formulas, and source rows to claims. |
| `notebook_generate` | Emit a reproducible notebook/script for the analysis. |
| `decision_log_write` | Record analyst conclusion, owner, timestamp, status, rationale, and linked evidence ids. |

### 12. Trust, Governance, And Ops

| Tool | Purpose |
|---|---|
| `access_policy_check` | Enforce tenant, role, row-level security, and column masking. |
| `audit_event_write` | Record who asked what, which queries ran, and what data was returned. |
| `privacy_redact` | Strip PII/secrets before model context. |
| `prompt_injection_scan` | Treat uploaded data as untrusted; block instructions embedded in rows. |
| `budget_guard` | Bound rows scanned, model calls, runtime, and token cost. |
| `analysis_replay` | Replay model/tool results without re-querying or re-running writes. |
| `artifact_version` | Version datasets, dashboards, charts, and model outputs. |
| `approval_request` | Require human approval for exports, broad access, or write actions. |

## Demo Milestones

### Milestone 1 — Local Analytics Agent

- [x] CSV upload/import into SQLite.
- [x] Profile, column profile, data quality, outlier, correlation, feature ranking, read-only SQL,
  causal caveat.
- [x] Group-by, pivot, distribution compare, approximate hypothesis test, regression feature screen,
  anomaly scan, simple forecast.
- [x] Chart spec generation and HTML dashboard artifact.
- [x] Deterministic tests with local sample data.
- [x] Metric catalog and semantic query plan for the demo dataset.
- [x] Query citations and persisted evidence manifest for dataset, semantic plan, governed SQL, and
  dashboard artifact events.
- [x] Analyst-authored persisted decision log with owner, conclusion, status, rationale, and linked
  evidence ids.

### Milestone 2 — Dashboard Builder

- [x] Basic chart spec generation.
- [x] Basic HTML dashboard export.
- [x] Dashboard object model with KPI tiles, charts, filters, layout, and drilldown metadata.
- [x] Follow-up dashboard updates that add/remove/rename tiles and preserve evidence.
- [ ] Interactive hosted dashboard UI.

### Milestone 3 — Multi-Table Semantic Analytics

- [ ] Import multiple CSVs or connect to a relational database.
- [x] Initial semantic metric/dimension definitions for the sample analytics dataset.
- [x] Initial governed SQL generation from natural language for metric-by-dimension questions.
- [ ] Relationship discovery and multi-table semantic metric definitions.

### Milestone 4 — Advanced Stats And ML

- [x] Approximate hypothesis test, simple regression screen, anomaly detection, simple forecasting.
- [ ] Full regression diagnostics, feature importance, clustering, model evaluation reports, model cards.
- [ ] Causal sanity checks with stronger designs where data supports them.

### Milestone 5 — Large Dataset And Warehouse Demo

- [x] Connector seam and local CSV connector.
- [x] Metadata-only external dataset references.
- [ ] Optional connectors for Hugging Face, Kaggle, and BigQuery public datasets.
- [ ] Sampling, pushdown SQL, query cost estimates, result caching.
- [ ] Demonstrate millions of rows without loading everything into model context.

## Public Dataset Candidates

Use local sample data for CI. Use these externally hosted datasets for richer manual demos.

### Small To Medium

| Dataset | Source | Why it is useful |
|---|---|---|
| Adult Census Income | [Hugging Face](https://huggingface.co/datasets/scikit-learn/adult-census-income) | Mixed numeric/categorical classification, fairness caveats, segment analysis. |
| Olist Brazilian E-Commerce | [Kaggle](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce) | Multi-table e-commerce analytics: customers, orders, reviews, payments, sellers. |
| Telco Customer Churn | [Kaggle](https://www.kaggle.com/datasets/blastchar/telco-customer-churn) | Churn drivers, categorical features, cohort-style product analytics. |
| Credit Card Fraud Detection | [Kaggle](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud) | Imbalanced classification, anomaly detection, precision/recall tradeoffs. |

### Large Or Warehouse-Scale

| Dataset | Source | Why it is useful |
|---|---|---|
| Inria SODA Tabular Benchmark | [Hugging Face](https://huggingface.co/datasets/inria-soda/tabular-benchmark) | Many tabular classification/regression subsets, from thousands to millions of rows. |
| M5 Forecasting Accuracy | [Kaggle](https://www.kaggle.com/competitions/m5-forecasting-accuracy) | Retail demand forecasting, hierarchy, calendar effects, price effects. |
| BigQuery Public Datasets | [Google Cloud](https://cloud.google.com/bigquery/public-data) | Warehouse-native demo with public SQL datasets and cost-aware query execution. |
| BigQuery Samples | [Google Cloud](https://cloud.google.com/bigquery/public-data#sample_tables) | Weather, GitHub, natality, Shakespeare, trigrams, Wikipedia sample tables. |

## What To Build Next

For the next PR slice, the best high-signal path is:

1. Replace the scripted analytics model with a planning agent that chooses tools from the question.
2. Add `analysis_plan_create`, `sql_validate`, `chart_recommend`, and `chart_spec_generate`.
3. Add an HTML dashboard artifact for the sample CSV.
4. Add a connector interface with implementations for local CSV and Hugging Face dataset references.
5. Add a larger manual demo path using `inria-soda/tabular-benchmark` or BigQuery public datasets.
