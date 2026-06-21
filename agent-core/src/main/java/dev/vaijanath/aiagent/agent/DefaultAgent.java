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
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprover;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolDecision;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L1 runtime: a guardrail-wrapped, observable agent loop.
 *
 * <pre>
 *   input guardrails -&gt; ( model call -&gt; optional tool calls )* -&gt; output guardrails
 * </pre>
 *
 * <p>Every step emits {@link AgentObserver} events (for tracing, metering, recording). Observer
 * callbacks are isolated: a throwing observer is logged and ignored, never breaking the run.
 */
public final class DefaultAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);

    private final ModelPort model;
    private final List<Guardrail> guardrails;
    private final Map<String, Tool> tools;
    private final ToolApprover toolApprover;
    private final Memory memory;
    private final List<AgentObserver> observers;
    private final String systemPrompt;
    private final int maxSteps;

    private boolean systemInstalled = false;

    private DefaultAgent(Builder b) {
        this.model = Objects.requireNonNull(b.model, "model");
        this.guardrails = List.copyOf(b.guardrails);
        this.observers = List.copyOf(b.observers);
        this.toolApprover = b.toolApprover != null ? b.toolApprover : ToolApprovers.allowAll();
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
        notify(o -> o.onTurnStart(request.input()));

        // 1. Input guardrails — block before anything reaches the model.
        GuardrailDecision in = applyGuardrails(GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            log.info("input blocked: {}", in.reason());
            return finish(AgentResponse.blocked(in.content(), in.reason()));
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
            ModelRequest req = new ModelRequest(memory.history(), toolSpecs);
            notify(o -> o.onModelCall(req));
            final ModelResponse resp;
            try {
                resp = model.chat(req);
            } catch (RuntimeException e) {
                // Graceful failure: surface it, never crash out of run().
                log.warn("model call failed; ending turn gracefully", e);
                notify(o -> o.onError("model", e));
                return finish(AgentResponse.stopped(
                        "I ran into a problem reaching the model. Please try again.", "model_error"));
            }
            notify(o -> o.onModelResponse(resp));

            if (resp.hasToolCalls()) {
                memory.add(Message.assistant(resp.text(), resp.toolCalls()));
                for (ToolCall call : resp.toolCalls()) {
                    notify(o -> o.onToolCall(call));
                    ToolResult result = invokeWithPolicy(call);
                    notify(o -> o.onToolResult(call.name(), result));
                    memory.add(Message.toolResult(call.id(), call.name(), result.content()));
                }
                continue; // let the model react to the tool results
            }

            finalText = (resp.text() == null) ? "" : resp.text();
            break;
        }

        if (finalText == null) {
            log.warn("agent stopped after maxSteps={} without a final answer", maxSteps);
            return finish(AgentResponse.stopped(
                    "I couldn't finish that within my step budget. Please try rephrasing.",
                    "max_steps"));
        }

        // 4. Output guardrails — nothing unsafe reaches the user.
        GuardrailDecision out = applyGuardrails(GuardrailStage.OUTPUT, finalText);
        memory.add(Message.assistant(out.content()));
        if (out.blocked()) {
            log.info("output blocked: {}", out.reason());
            return finish(AgentResponse.blocked(out.content(), out.reason()));
        }
        return finish(AgentResponse.completed(out.content()));
    }

    private AgentResponse finish(AgentResponse response) {
        notify(o -> o.onTurnEnd(response));
        return response;
    }

    /** Runs guardrails in order; the first block wins, transformations chain. */
    private GuardrailDecision applyGuardrails(GuardrailStage stage, String content) {
        String current = content;
        for (Guardrail g : guardrails) {
            GuardrailDecision d = g.check(stage, current);
            notify(o -> o.onGuardrail(stage, g.name(), d));
            if (d.blocked()) {
                return d;
            }
            current = d.content();
        }
        return GuardrailDecision.allow(current);
    }

    /** Authorizes a tool call, then runs it (or returns a denial the model can react to). */
    private ToolResult invokeWithPolicy(ToolCall call) {
        ToolDecision decision = toolApprover.authorize(call.name(), call.argumentsJson());
        if (!decision.allowed()) {
            log.info("tool '{}' denied: {}", call.name(), decision.reason());
            return ToolResult.error("tool '" + call.name() + "' not permitted: " + decision.reason());
        }
        Tool tool = tools.get(call.name());
        return (tool == null)
                ? ToolResult.error("unknown tool: " + call.name())
                : safeInvoke(tool, call.argumentsJson());
    }

    private ToolResult safeInvoke(Tool tool, String args) {
        try {
            return tool.invoke(args);
        } catch (RuntimeException e) {
            log.warn("tool '{}' threw", tool.name(), e);
            return ToolResult.error("tool '" + tool.name() + "' failed: " + e.getMessage());
        }
    }

    /** Dispatches an event to every observer, isolating failures so they never break the run. */
    private void notify(Consumer<AgentObserver> event) {
        for (AgentObserver o : observers) {
            try {
                event.accept(o);
            } catch (RuntimeException e) {
                log.warn("observer {} threw; ignoring", o.getClass().getSimpleName(), e);
            }
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
        private final List<AgentObserver> observers = new ArrayList<>();
        private ToolApprover toolApprover;
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

        public Builder observer(AgentObserver observer) {
            this.observers.add(observer);
            return this;
        }

        /** Gate tool execution behind a policy (allow-list, human approval, …). Default: allow all. */
        public Builder toolApprover(ToolApprover toolApprover) {
            this.toolApprover = toolApprover;
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
