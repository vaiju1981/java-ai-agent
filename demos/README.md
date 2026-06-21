# Demos

Real-world demos showing what java-ai-agent is good for. They need a model:

```bash
export AGENT_MODEL=gemma4:31b-cloud   # any pulled, tool-capable Ollama model
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.<Demo>
```

## DataAnalystDemo — works at scale

Generates a synthetic **SQLite database of 5,000 transactions**, then answers natural-language
questions by having the model write **SQL that a read-only tool executes**. The point: the rows
never enter the prompt — only each query's aggregated result does — so it scales to large datasets.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.DataAnalystDemo
```

Sample (verified live): "total spent per category", "top-5 merchants", "count over $200 + average"
— each answered by a single generated `SELECT` over all 5,000 rows.

## PersonalFinanceDemo — many tools, the model picks the right one

A personal-finance assistant with a realistic **~24-tool** toolkit: `categorize_merchant`,
`budget_check`, `sql` (over the transactions), plus ~20 finance calculators (compound interest, loan
payment, tip, savings rate, ROI, …). All tools are presented at once — so this also **validates that
the agent picks the correct tool when there are many**.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.PersonalFinanceDemo
```

Sample (verified live, 24 tools): categorizes Blue Bottle → Dining; flags Entertainment over budget;
finds Travel as the top category (sql); computes the future value of an investment, a mortgage
payment, and an 18% tip — each routed to the right tool, with correct math.

(The runtime is also unit-tested to dispatch correctly across **40** tools, and a `ToolSelector` can
present only the relevant subset per turn when a toolkit grows larger still.)

## LogAnalystDemo — scale, a different dataset

A site-reliability analyst over **~10,000 synthetic request logs** in SQLite (level, endpoint,
status, latency). Same SQL-tool scaling story as the data analyst, different shape.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.LogAnalystDemo
```

Sample (verified live): overall error rate (3.91%), the five highest-latency endpoints, and the
endpoint producing the most ERRORs — each from one generated `SELECT` over all 10,000 rows.

## SupportTriageDemo — structured classification

Triages a batch of synthetic support tickets into structured `{priority, category, team}` using
**structured output** (JSON bound straight to a record — no parsing), then prints a summary tally.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.SupportTriageDemo
```

Sample (verified live): "Urgent: data appears deleted" → urgent / Bug / Engineering; "Refund still
not received" → high / Billing / Payments — with category and priority counts at the end.

## SafeHealthDemo — the trust layer on sensitive data

A health-literacy assistant: explains lab values using a curated `lab_reference` tool, behind
kidguard guardrails (crisis + PII + Llama Guard), under a strict **explain-never-diagnose** rule.

```bash
ollama pull llama-guard3:1b
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.SafeHealthDemo
```

Sample (verified live): explains glucose 110 / LDL 160 against their reference ranges with a "discuss
with your doctor" and no diagnosis; a distress message is **blocked by the crisis guardrail** with a
supportive response before the model is ever called.

## ResearchBriefingDemo — a deep agent

Plans a topic into sections (via structured output), writes each with a **concurrent sub-agent**, and
synthesizes a briefing — printing the plan, the per-section workspace drafts, and the final document.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.ResearchBriefingDemo
```

Sample (verified live): a balanced "Kotlin vs Java for a 2026 backend" briefing with a recommendation,
assembled from independently-written sections. (The deep-agent orchestration is covered by the core
test suite.)
