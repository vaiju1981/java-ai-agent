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

## PersonalFinanceDemo — many tools, the model picks

A personal-finance assistant over the same data with three tools — `categorize_merchant` (classify a
merchant), `budget_check` (spend vs. monthly budget), and `sql` (anything else, at scale). The model
chooses the right tool per question.

```bash
./gradlew :demos:run -PmainClass=dev.vaijanath.aiagent.demos.PersonalFinanceDemo
```

Sample (verified live): classifies "Blue Bottle Coffee" → Dining; flags Entertainment as over budget
in March; identifies Travel as the biggest category and suggests where to cut back.
