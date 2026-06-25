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

## 1b. A typed interface over an agent (`@AiService`)

Prefer a plain Java interface to a builder call site? Wrap any governed `Agent` as a typed service —
each method becomes one agent turn (`{{...}}` placeholders fill from `@V` parameters).

```java
interface SupportBot {
    String answer(String question);

    @UserMessage("Summarize in {{words}} words:\n{{text}}")
    String summarize(@V("text") String text, @V("words") int words);
}

SupportBot bot = AiServices.create(SupportBot.class, agent); // agent = any governed Agent
String reply = bot.answer("How do refunds work?");
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

## 4. Talk to Claude or OpenAI directly (`agent-anthropic` / `agent-openai`)

First-party `ModelPort`s over the official Anthropic and OpenAI SDKs — no intermediary framework. Each
reads its API key from the environment and defaults to a current model.

```java
ModelPort claude = AnthropicModelPort.fromEnv();   // ANTHROPIC_API_KEY; or fromEnv("claude-opus-4-8")
ModelPort openai = OpenAiModelPort.fromEnv();       // OPENAI_API_KEY;    or fromEnv("gpt-4o")
```

### Multimodal: send an image to a vision model

Attach `Media` (images/audio) to a user turn with `Message.user(text, media)`. The first-party Anthropic
and OpenAI adapters translate images to provider image blocks (inline base64 or a URL); text-only models
ignore media. Audio is carried in the model for adapters that support it.

```java
byte[] png = Files.readAllBytes(Path.of("chart.png"));
ModelResponse answer = claude.chat(ModelRequest.of(List.of(
        Message.user("What trend does this chart show?", List.of(Media.image("image/png", png))))));
// or by reference: Media.imageUrl("https://example.com/chart.png")
```

## 5. Retrieval-augmented answers (RAG)

Ground the agent in retrieved context. Use the in-memory store for small corpora, or `JdbcVectorStore`
(`agent-store-jdbc`) for a durable one on SQLite/PostgreSQL. For large corpora, `PgVectorRetriever`
(`agent-store-pgvector`) does true approximate-nearest-neighbour search via PostgreSQL + pgvector's
HNSW index instead of an in-application scan. Bridge a real embedder with `LangChain4jEmbedder`
(`agent-langchain4j`).

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

For a manager that loops (delegate → inspect → re-delegate) use `ManagerAgent`; for a task fanned
out to parallel sub-agents with dependencies, use `DeepAgent` (a DAG with data flow).

For the **Swarm** pattern — peers that hand control to one another (no central manager) — use
`HandoffAgent`: a start agent handles the request, then a `Handoff` decides whether a peer takes over.

```java
Agent swarm = HandoffAgent.builder()
        .agent("triage", "routes incoming requests", triageAgent)
        .agent("billing", "billing, invoices, refunds", billingAgent)
        .start("triage")
        .handoff(new LlmHandoff(model))  // after each hop: transfer to a peer, or STAY
        .build();
// Share a ConversationStore across the agents for true handoff continuity (each peer sees the history).
```

For a **group chat** — several agents sharing one transcript, taking turns (the AutoGen pattern) — use
`GroupChatAgent`: a `SpeakerSelector` picks who speaks next; every agent sees the whole conversation.

```java
Agent panel = GroupChatAgent.builder()
        .agent("optimist", "argues for the idea", optimistAgent)
        .agent("skeptic", "argues against it", skepticAgent)
        .agent("judge", "weighs both and concludes", judgeAgent)
        .selector(new LlmSpeakerSelector(model))  // or RoundRobinSelector; LLM can end with DONE
        .maxRounds(6)
        .build();
```

For an explicit **workflow graph** with branches and loops, use `GraphAgent`: named nodes (each any
`Agent`) joined by edges, walked until an edge reaches `END`. Conditional edges enable cycles
(e.g. draft → review → back to draft until approved), and an optional `CheckpointStore` makes the
walk crash-resumable.

```java
Agent flow = GraphAgent.builder()
        .node("draft", draftAgent)
        .node("review", reviewAgent)
        .start("draft")
        .edge("draft", "review")
        .edge("review", state -> state.contains("APPROVED") ? GraphAgent.END : "draft") // loop back
        .checkpoints(checkpointStore) // optional: resume mid-graph after a crash
        .build();
```

## 6b. Agents as tools — agents calling agents (`agent-core`)

Wrap an `Agent` as a `Tool` and the calling model decides, per turn, which specialist to invoke and
with what request — peer agents collaborating, no extra orchestration code. The caller's
identity/tenant/trace/deadline flow into the specialist, and its guardrails still apply.

```java
Agent manager = DefaultAgent.builder()
        .model(model)
        .systemPrompt("Coordinate the specialists to answer the user.")
        .tool(Agents.asTool("research", "gathers and verifies facts", researchAgent))
        .tool(Agents.asTool("write", "drafts prose", writerAgent, ToolEffect.READ_ONLY))
        .build();

manager.run(new AgentRequest("Research virtual threads and write a 3-line summary."));
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
