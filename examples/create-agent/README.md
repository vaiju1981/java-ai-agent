# create-agent — a java-ai-agent starter

The smallest runnable [java-ai-agent](https://github.com/vaiju1981/java-ai-agent) app: a model and a
prompt. **Copy this folder out of the repo** and make it the seed of your own project.

```
create-agent/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/java/com/example/Main.java
```

## Run it

1. **Get an API key** and export it:
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-...     # talk to Claude (the default)
   # or, for GPT: depend on agent-openai instead and export OPENAI_API_KEY
   ```
2. **Run** (Gradle 8+; if you don't have a wrapper yet, run `gradle wrapper` first):
   ```bash
   gradle run --args="What can you help me build?"
   ```

That's it — you have a working agent.

## What's next

This starter is rung one. The same `Agent` seam grows into everything else:

- **Tools** — give the agent typed Java methods to call (`agent-tools-annotations`).
- **Guardrails & approval** — wrap it with `Trust.govern(...)` for safety + human-in-the-loop.
- **Memory** — persist conversations (`agent-store-jdbc`) or add semantic recall.
- **RAG** — ground answers in your documents (`DocumentSplitter` + `Ingestor` + a vector store).
- **Multi-agent** — route to specialists, hand off between peers, run a workflow graph, or call a
  remote agent over A2A — all composing on the one `Agent` interface.

See the [Cookbook](https://github.com/vaiju1981/java-ai-agent/blob/main/docs/COOKBOOK.md) for copy-paste
recipes for each.
