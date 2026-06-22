# Cookbook

Short, copy-pasteable recipes for building agents with java-ai-agent. Each builds on `agent-core`
plus whichever substrate/capability module the recipe needs. For the *why*, see
[DESIGN.md](../DESIGN.md); for module layout, the [README](../README.md).

---

## 1. A governed agent, assembled by hand

`ProductionAgentRuntime` wires the trust layer (durable store, audit, argument validation, timeouts,
deny-effectful tool authorization, a hard per-turn deadline) around any `ModelPort`.

```java
Agent agent = ProductionAgentRuntime.builder()
        .model(model)                                   // any ModelPort (see recipe 4)
        .conversationStore(new InMemoryConversationStore())
        .auditSink(new InMemoryAuditSink())
        .argumentValidator(new JsonSchemaToolValidator())
        .systemPrompt("You are a concise, accurate assistant.")
        .build();

AgentResponse response = agent.run(new AgentRequest("Hello!"));
System.out.println(response.output());
```

## 2. Tools as plain methods (`agent-tools-annotations`)

Annotate methods instead of hand-writing JSON schemas; `ReflectiveTools` derives the schema and binds
arguments.

```java
class WeatherTools {
    @AgentTool(description = "Current weather for a city", effect = ToolEffect.READ_ONLY)
    public String getWeather(@ToolParam(description = "city name") String city) {
        return "Sunny in " + city;
    }
}

List<Tool> tools = ReflectiveTools.from(new WeatherTools());
// add each to the runtime builder: tools.forEach(builder::tool)
```

## 3. Typed (structured) final output (`agent-core`)

Run the tool-calling loop, then coerce the answer into a record — no manual parsing.

```java
record Sentiment(String label, double confidence) {}

StructuredAgent structured = new StructuredAgent(agent, structuredOutput); // StructuredOutput from an adapter
StructuredResult<Sentiment> result = structured.run("Review: best purchase ever!", Sentiment.class);
if (result.present()) {
    System.out.println(result.value().label());
}
```

## 4. Talk to Claude directly (`agent-anthropic`)

A first-party `ModelPort` over the official Anthropic SDK — no intermediary framework. Reads
`ANTHROPIC_API_KEY` and defaults to the latest Claude model.

```java
ModelPort model = AnthropicModelPort.fromEnv();            // or fromEnv("claude-opus-4-8")
```

## 5. Retrieval-augmented answers (RAG)

Ground the agent in retrieved context. Use the in-memory store for small corpora, or `JdbcVectorStore`
(`agent-store-jdbc`) for a durable one on SQLite/PostgreSQL. Bridge a real embedder with
`LangChain4jEmbedder` (`agent-langchain4j`).

```java
InMemoryVectorStore store = new InMemoryVectorStore(embedder);
store.add("default", "doc-1", "The refund window is 30 days.");
store.add("default", "doc-2", "Express shipping is free over $50.");

Agent grounded = new RetrievalAugmentedAgent(agent, store); // retrieves top-k, weaves into the prompt
grounded.run(new AgentRequest("How long do I have to return something?"));
```

## 6. Route to specialists (supervisor / handoff)

```java
Agent supervisor = SupervisorAgent.builder()
        .specialist("billing", "billing, invoices, refunds", billingAgent)
        .specialist("weather", "forecasts and conditions", weatherAgent)
        .router(new LlmRouter(model))   // or any Router, e.g. a keyword rule
        .build();                        // unknown routes fall back to the first specialist
```

## 7. Smarter memory

Bound context by tokens, or summarize older turns instead of dropping them.

```java
Tokenizer tokenizer = new HeuristicTokenizer();
Memory windowed = new TokenWindowedMemory(tokenizer, 4_000);
Memory summarizing = new SummarizingMemory(tokenizer, summarizer, 4_000, /* keep recent */ 6);
```

## 8. Guardrails

Compose the `kidguard` safety pipeline and add prompt-injection screening.

```java
List<Guardrail> guardrails = new ArrayList<>(Guardrails.kidguard(guardModel)); // crisis + PII + LlamaGuard
guardrails.add(new InjectionGuardrail());                                       // blocks injection on input
guardrails.forEach(builder::guardrail);
```

## 9. Spring Boot (`agent-spring-boot-starter`)

Declare a `ModelPort` bean; the starter autoconfigures a governed `Agent` (override any default by
declaring your own bean). Configure via `agent.*` properties.

```java
@Bean
ModelPort modelPort() {
    return AnthropicModelPort.fromEnv();
}

@Autowired Agent agent; // autoconfigured — inject and use
```

```properties
agent.system-prompt=You are a concise production assistant.
agent.model-timeout=60s
agent.max-steps=8
```

## 10. Stream a turn over HTTP (`production-reference`)

`POST /api/agent/turn/stream` streams Server-Sent Events: a `tool` event per tool call, a
`tool_result` event per result, then a `final` event with the guarded `AgentResponse`. Raw
pre-guardrail tokens are intentionally not streamed (output guardrails run on the final answer).
