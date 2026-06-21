package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.memory.InMemoryMemory;
import dev.vaijanath.aiagent.memory.Memory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Phase 0 L1 runtime: a guardrail-wrapped agent loop.
 *
 * <pre>
 *   input guardrails -&gt; ( model call -&gt; optional tool calls )* -&gt; output guardrails
 * </pre>
 *
 * <p>Tool execution is wired but only fires when a {@link ModelPort} returns tool calls (the
 * text-only Phase 0 ports never do); the structure is in place for Phase 1.
 */
public final class DefaultAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);

    private final ModelPort model;
    private final List<Guardrail> guardrails;
    private final Map<String, Tool> tools;
    private final Memory memory;
    private final String systemPrompt;
    private final int maxSteps;

    private boolean systemInstalled = false;

    private DefaultAgent(Builder b) {
        this.model = Objects.requireNonNull(b.model, "model");
        this.guardrails = List.copyOf(b.guardrails);
        this.memory = b.memory != null ? b.memory : new InMemoryMemory();
        this.systemPrompt = b.systemPrompt;
        this.maxSteps = b.maxSteps;
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : b.tools) {
            map.put(tool.name(), tool);
        }
        this.tools = Map.copyOf(map);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        // 1. Input guardrails — block before anything reaches the model.
        GuardrailDecision in = applyGuardrails(GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            log.info("input blocked: {}", in.reason());
            return AgentResponse.blocked(in.content(), in.reason());
        }

        // 2. Seed the conversation.
        if (!systemInstalled && systemPrompt != null && !systemPrompt.isBlank()) {
            memory.add(Message.system(systemPrompt));
            systemInstalled = true;
        }
        memory.add(Message.user(in.content()));

        // 3. Model / tool loop.
        List<ToolSpec> toolSpecs = tools.values().stream().map(Tool::spec).toList();
        String finalText = null;
        for (int step = 0; step < maxSteps; step++) {
            ModelResponse resp = model.chat(new ModelRequest(memory.history(), toolSpecs));

            if (resp.hasToolCalls()) {
                resp.toolCalls().forEach(call -> {
                    Tool tool = tools.get(call.name());
                    ToolResult result = (tool == null)
                            ? ToolResult.error("unknown tool: " + call.name())
                            : safeInvoke(tool, call.argumentsJson());
                    memory.add(Message.tool(result.content()));
                });
                continue; // let the model react to the tool results
            }

            finalText = (resp.text() == null) ? "" : resp.text();
            break;
        }

        if (finalText == null) {
            log.warn("agent stopped after maxSteps={} without a final answer", maxSteps);
            return AgentResponse.stopped(
                    "I couldn't finish that within my step budget. Please try rephrasing.",
                    "max_steps");
        }

        // 4. Output guardrails — nothing unsafe reaches the user.
        GuardrailDecision out = applyGuardrails(GuardrailStage.OUTPUT, finalText);
        memory.add(Message.assistant(out.content()));
        if (out.blocked()) {
            log.info("output blocked: {}", out.reason());
            return AgentResponse.blocked(out.content(), out.reason());
        }
        return AgentResponse.completed(out.content());
    }

    /** Runs guardrails in order; the first block wins, transformations chain. */
    private GuardrailDecision applyGuardrails(GuardrailStage stage, String content) {
        String current = content;
        for (Guardrail g : guardrails) {
            GuardrailDecision d = g.check(stage, current);
            if (d.blocked()) {
                return d;
            }
            current = d.content();
        }
        return GuardrailDecision.allow(current);
    }

    private ToolResult safeInvoke(Tool tool, String args) {
        try {
            return tool.invoke(args);
        } catch (RuntimeException e) {
            log.warn("tool '{}' threw", tool.name(), e);
            return ToolResult.error("tool '" + tool.name() + "' failed: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — wire an agent in a few lines. */
    public static final class Builder {
        private ModelPort model;
        private final List<Guardrail> guardrails = new ArrayList<>();
        private final List<Tool> tools = new ArrayList<>();
        private Memory memory;
        private String systemPrompt;
        private int maxSteps = 8;

        public Builder model(ModelPort model) {
            this.model = model;
            return this;
        }

        public Builder guardrail(Guardrail guardrail) {
            this.guardrails.add(guardrail);
            return this;
        }

        public Builder tool(Tool tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder memory(Memory memory) {
            this.memory = memory;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public DefaultAgent build() {
            return new DefaultAgent(this);
        }
    }
}
