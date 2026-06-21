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
