# Demos

A small set of **deep, production-shaped** applications built on java-ai-agent — the proof that real
work runs on the library, not a gallery of skeletons. Each needs a tool-capable model:

```bash
export AGENT_MODEL=gemma4:31b-cloud   # any pulled, tool-capable Ollama model
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.<group>.<Demo>
```

**Every demo runs through the governed production runtime** (`ProductionAgentRuntime`, via the shared
`Governed` helper) — the same wiring the [`production-reference`](../production-reference/README.md)
service uses. So each turn is **deadline-bounded**, its tool arguments are **schema-validated**,
effectful tools are **denied by default**, and the whole run is **audited and token-metered**, with a
**trust report** (token usage + audit trail) printed after the run.

## GovernedSupportDeskDemo — the flagship (no model needed)

A self-contained tour of the entire trust layer on one governed support-desk agent. It uses a
**scripted model**, so every guarantee is **deterministic and reproducible without Ollama**:

1. durable conversation history that **survives a "restart"** (real SQLite/PostgreSQL store);
2. an effectful refund tool **denied by default**, then the same tool **allow-listed**;
3. guardrails that **block a crisis input** and **scrub PII** before it reaches the model;
4. a **token budget** that stops the run gracefully when exhausted;
5. **tenant isolation** — the same session id under two tenants never crosses over;
6. a printed **audit trail** of who did what.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.flagship.GovernedSupportDeskDemo
```

Start here to see what "production usage" means; the analytical demos below put a live model to work.

## DataAnalystDemo — a real exploratory data analyst

A senior data analyst over a **multi-table e-commerce warehouse** (~1,500 customers, ~12,000 orders,
~30,000 order lines), exposed as a denormalized `order_lines` mart. It is given an **actual EDA
toolkit** — not just `GROUP BY`:

- **`profile_column`** — numeric stats (count/nulls/min/max/mean/median/stddev/p25/p75) or, for
  categorical columns, distinct count and top values;
- **`histogram`** — binned distribution of a numeric column;
- **`outliers`** — Tukey 1.5×IQR fences with the most extreme examples;
- **`correlation`** — Pearson between two columns, with an interpretation;
- **`group_stats`** — one- or two-dimension segmentation (hierarchical drill-down);
- **`time_series`** — trend/seasonality over month or quarter buckets with period-over-period change;
- **`driver_analysis`** — contribution-to-change: *which segment drove a metric's move between two
  periods*, ranked;
- plus schema discovery and a read-only `sql` escape hatch. Optional category filters are bound as
  parameters; every identifier is validated, so a tool argument can't smuggle SQL.

The data has **planted signal to discover** (see `EcommerceData`): Q4 seasonality, a West-region Q3
dip, thin Electronics margins, a Cables return hotspot, promo→volume elasticity, bulk-order outliers,
and an early-signup cohort. It runs as **one end-to-end investigation** — explore & quality-check,
trend/seasonality, *explain the Q3 dip*, margins, promo elasticity, return hotspot, recommendations —
and **writes each result to a persisted report** (`record_finding`), printed as a structured markdown
deliverable at the end. Rows stay in SQLite; only aggregates enter the model, so it scales.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.data.DataAnalystDemo
```

The toolkit's correctness — that it actually finds the planted signal (positive promo elasticity,
Electronics as the thinnest-margin category, the West as the Q3 driver, Q4 seasonality, the bulk
outliers) — is covered deterministically by `AnalyticsToolsTest`. Verified live: the agent reports
"West drove the Q3 decline, −$861,818 (~39%)", "Electronics thinnest at $44.63 avg margin", and
"Cables return rate 24.34% vs 9.28% average".

## FraudInvestigationDemo — analysis + governed, effectful action

The domain that exercises the trust layer's differentiator: **effectful actions under governance**.
Over a payments book with planted fraud (card testing, a velocity spike, a geo-anomaly, a transfer
ring), the agent ranks accounts by risk signals (`suspicious_accounts`), drills into the standouts
(`account_summary`, `recent_transactions`), writes a **case report**, and then **acts** —
`flag_for_review` and `freeze_account`, implemented as `ContextualTool`s so they run under the
governed runtime with the principal and an idempotency key.

Authorization is **graduated**: the policy is deny-by-default with only `flag_for_review`
allow-listed. So the agent flags suspicious accounts autonomously, but a `freeze_account` attempt is
**denied** — it needs human authorization the agent doesn't have, and that denial is audited. Actions
are **idempotent** (a retry never double-freezes).

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.fraud.FraudInvestigationDemo
```

`FraudToolsTest` proves it deterministically: the risk signals surface the planted accounts, actions
are idempotent, and the runtime allows the flag while denying the freeze.

## PersonalFinanceDemo — many tools, the model picks the right one

A personal-finance assistant with a realistic **~24-tool** toolkit: data lookups
(`categorize_merchant`, `budget_check`, `sql`), data-aware analyses over the actual transactions
(`top_merchants`, `recurring_subscriptions`, `spending_by_category`, `category_budget_status`,
`largest_expenses`, `monthly_spending`), and planning calculators (compound interest, loan payment,
tip, savings rate, ROI, …) — so it also validates that the agent picks the right tool among many.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.finance.PersonalFinanceDemo
```
