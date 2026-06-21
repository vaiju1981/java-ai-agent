package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.audit.AuditEvent;
import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.memory.InMemoryConversationStore;
import dev.vaijanath.aiagent.memory.InMemoryMemory;
import dev.vaijanath.aiagent.memory.Memory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelPorts;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprover;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolDecision;
import dev.vaijanath.aiagent.tool.ToolExecutor;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSelector;
import dev.vaijanath.aiagent.tool.ToolSelectors;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L1 runtime: a guardrail-wrapped, observable agent loop.
 *
 * <pre>
 *   input guardrails -&gt; ( model call -&gt; optional tool calls )* -&gt; output guardrails
 * </pre>
 *
 * <p>The agent is <b>stateless across calls</b>: conversation memory is scoped to the request's
 * {@link RequestContext#sessionId()} via a {@link ConversationStore}, so one instance can serve many
 * sessions, users, and tenants concurrently without their histories interleaving. Work on a single
 * session is serialized so that session's memory stays consistent.
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
    private final ToolSelector toolSelector;
    private final ToolExecutor toolExecutor;
    private final ConversationStore conversations;
    private final List<AgentObserver> observers;
    private final AuditSink auditSink;
    private final String systemPrompt;
    private final int maxSteps;
    private final boolean streamRawTokens;
    private final Duration toolTimeout;

    private DefaultAgent(Builder b) {
        this.model = Objects.requireNonNull(b.model, "model");
        this.guardrails = List.copyOf(b.guardrails);
        this.observers = List.copyOf(b.observers);
        this.toolApprover = b.toolApprover != null ? b.toolApprover : ToolApprovers.allowAll();
        this.toolSelector = b.toolSelector != null ? b.toolSelector : ToolSelectors.all();
        this.toolExecutor = b.toolExecutor;
        this.conversations = b.conversationStore != null
                ? b.conversationStore
                : new InMemoryConversationStore(
                        b.memoryFactory != null ? b.memoryFactory : InMemoryMemory::new);
        this.auditSink = b.auditSink != null ? b.auditSink : AuditSink.none();
        this.systemPrompt = b.systemPrompt;
        this.maxSteps = b.maxSteps;
        this.streamRawTokens = b.streamRawTokens;
        this.toolTimeout = b.toolTimeout;
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : b.tools) {
            map.put(tool.name(), tool);
        }
        this.tools = Map.copyOf(map);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        RequestContext ctx = request.context();
        notify(o -> o.onTurnStart(request.input()));
        audit("turn.start", ctx, "input.len=" + request.input().length());

        // 1. Input guardrails — block before anything reaches the model or memory.
        GuardrailDecision in = applyGuardrails(GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            log.info("input blocked: {}", in.reason());
            audit("guardrail.block", ctx, "stage=INPUT reason=" + in.reason());
            return finish(AgentResponse.blocked(in.content(), in.reason()));
        }

        // 2. Serialize work on this session so its memory stays consistent; different sessions
        //    (different users/tenants) use different memory objects and run concurrently.
        Memory memory = conversations.get(ctx.tenant(), ctx.sessionId());
        synchronized (memory) {
            return converse(ctx, in.content(), memory);
        }
    }

    /** The model/tool loop for one turn, holding the session's memory lock. */
    private AgentResponse converse(RequestContext ctx, String userContent, Memory memory) {
        if (memory.history().isEmpty() && systemPrompt != null && !systemPrompt.isBlank()) {
            memory.add(Message.system(systemPrompt));
        }
        memory.add(Message.user(userContent));

        // Present only the tools the selector deems relevant (default: all), so an agent with dozens
        // of tools still shows the model a focused, manageable set.
        List<Tool> activeTools = toolSelector.select(userContent, List.copyOf(tools.values()));
        List<ToolSpec> toolSpecs = activeTools.stream().map(Tool::spec).toList();
        Map<String, Tool> activeByName = new LinkedHashMap<>();
        for (Tool t : activeTools) {
            activeByName.put(t.name(), t);
        }

        String finalText = null;
        for (int step = 0; step < maxSteps; step++) {
            if (deadlineExceeded(ctx)) {
                log.info("turn deadline exceeded for session {}", ctx.sessionId());
                audit("turn.end", ctx, "deadline_exceeded");
                return finish(AgentResponse.stopped(
                        "I ran out of time on this request.", "deadline_exceeded"));
            }
            ModelRequest req = new ModelRequest(memory.history(), toolSpecs);
            notify(o -> o.onModelCall(req));
            final ModelResponse resp;
            try {
                // Raw tokens are pre-guardrail and therefore unsafe, so they are NOT streamed by
                // default — the turn's result is delivered only after output guardrails run. A caller
                // that explicitly accepts an unguarded live stream opts in with streamRawTokens(true).
                resp = streamRawTokens
                        ? ModelPorts.stream(model, req, this::emitToken)
                        : model.chat(req);
            } catch (RuntimeException e) {
                // Graceful failure: surface it, never crash out of run().
                log.warn("model call failed; ending turn gracefully", e);
                notify(o -> o.onError("model", e));
                audit("error", ctx, "model call failed");
                return finish(AgentResponse.stopped(
                        "I ran into a problem reaching the model. Please try again.", "model_error"));
            }
            notify(o -> o.onModelResponse(resp));

            if (resp.hasToolCalls()) {
                memory.add(Message.assistant(resp.text(), resp.toolCalls()));
                for (ToolCall call : resp.toolCalls()) {
                    notify(o -> o.onToolCall(call));
                    // A configured executor (e.g. ReplayToolExecutor) overrides real execution;
                    // otherwise authorize and invoke the real tool.
                    ToolResult result = (toolExecutor != null)
                            ? toolExecutor.execute(call.name(), call.argumentsJson())
                            : invokeWithPolicy(call, activeByName, ctx);
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
            audit("turn.end", ctx, "max_steps");
            return finish(AgentResponse.stopped(
                    "I couldn't finish that within my step budget. Please try rephrasing.",
                    "max_steps"));
        }

        // Output guardrails — nothing unsafe reaches the user.
        GuardrailDecision out = applyGuardrails(GuardrailStage.OUTPUT, finalText);
        memory.add(Message.assistant(out.content()));
        if (out.blocked()) {
            log.info("output blocked: {}", out.reason());
            audit("guardrail.block", ctx, "stage=OUTPUT reason=" + out.reason());
            return finish(AgentResponse.blocked(out.content(), out.reason()));
        }
        audit("turn.end", ctx, "completed");
        return finish(AgentResponse.completed(out.content()));
    }

    private boolean deadlineExceeded(RequestContext ctx) {
        return ctx.deadlineAt().map(d -> !Instant.now().isBefore(d)).orElse(false);
    }

    private void audit(String type, RequestContext ctx, String detail) {
        auditSink.record(AuditEvent.now(
                type, ctx.traceId(), ctx.sessionId(), ctx.principal(), ctx.tenant(), detail));
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

    /**
     * Enforces the selector, then authorizes, then runs the tool. A tool that was not presented this
     * turn cannot be invoked — even if the model names it (hallucination or prompt injection) — and a
     * denied call becomes a result the model can react to rather than an execution.
     */
    private ToolResult invokeWithPolicy(ToolCall call, Map<String, Tool> available, RequestContext ctx) {
        Tool tool = available.get(call.name());
        if (tool == null) {
            log.info("tool '{}' not available this turn", call.name());
            audit("tool.denied", ctx, "tool=" + call.name() + " reason=not-available");
            return ToolResult.error("tool '" + call.name() + "' is not available");
        }
        ToolDecision decision = toolApprover.authorize(
                new ToolCallContext(tool.spec(), call.argumentsJson(), ctx.principal(), ctx.tenant()));
        if (!decision.allowed()) {
            log.info("tool '{}' denied: {}", call.name(), decision.reason());
            audit("tool.denied", ctx, "tool=" + call.name() + " reason=" + decision.reason());
            return ToolResult.error("tool '" + call.name() + "' not permitted: " + decision.reason());
        }
        audit("tool.allowed", ctx, "tool=" + call.name() + " effect=" + tool.spec().effect());
        return safeInvoke(tool, call.argumentsJson());
    }

    private ToolResult safeInvoke(Tool tool, String args) {
        if (toolTimeout == null) {
            return invokeDirect(tool, args);
        }
        // Bound the call on a virtual thread so a hung tool cannot stall the turn forever.
        FutureTask<ToolResult> task = new FutureTask<>(() -> invokeDirect(tool, args));
        Thread worker = Thread.ofVirtual().name("tool-" + tool.name()).start(task);
        try {
            return task.get(toolTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            worker.interrupt();
            log.warn("tool '{}' timed out after {}", tool.name(), toolTimeout);
            return ToolResult.error("tool '" + tool.name() + "' timed out after " + toolTimeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error("tool '" + tool.name() + "' was interrupted");
        } catch (ExecutionException e) {
            return ToolResult.error("tool '" + tool.name() + "' failed: " + e.getCause());
        }
    }

    private ToolResult invokeDirect(Tool tool, String args) {
        try {
            return tool.invoke(args);
        } catch (RuntimeException e) {
            log.warn("tool '{}' threw", tool.name(), e);
            return ToolResult.error("tool '" + tool.name() + "' failed: " + e.getMessage());
        }
    }

    private void emitToken(String token) {
        notify(o -> o.onToken(token));
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
        private AuditSink auditSink;
        private ToolApprover toolApprover;
        private ToolSelector toolSelector;
        private ToolExecutor toolExecutor;
        private Supplier<Memory> memoryFactory;
        private ConversationStore conversationStore;
        private String systemPrompt;
        private int maxSteps = 8;
        private boolean streamRawTokens = false;
        private Duration toolTimeout;

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

        /** Durably record an audit trail of turns (tool decisions, guardrail blocks, lifecycle). */
        public Builder auditSink(AuditSink auditSink) {
            this.auditSink = auditSink;
            return this;
        }

        /** Gate tool execution behind a policy (allow-list, human approval, …). Default: allow all. */
        public Builder toolApprover(ToolApprover toolApprover) {
            this.toolApprover = toolApprover;
            return this;
        }

        /** Choose which tools to present per turn (e.g. relevant subset of many). Default: all. */
        public Builder toolSelector(ToolSelector toolSelector) {
            this.toolSelector = toolSelector;
            return this;
        }

        /**
         * Override how tool calls become results — e.g. a {@code ReplayToolExecutor} for
         * side-effect-free replay. When set, it replaces the authorize-then-invoke path, so use it
         * only for replay or testing, never to bypass authorization in production.
         */
        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        /** How to create per-session conversation memory (default: in-memory). */
        public Builder memoryFactory(Supplier<Memory> memoryFactory) {
            this.memoryFactory = memoryFactory;
            return this;
        }

        /** Full control over where session memory lives (advanced; overrides {@code memoryFactory}). */
        public Builder conversationStore(ConversationStore conversationStore) {
            this.conversationStore = conversationStore;
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

        /** Bound each tool call; on timeout the call returns an error instead of hanging the turn. */
        public Builder toolTimeout(Duration toolTimeout) {
            this.toolTimeout = toolTimeout;
            return this;
        }

        /**
         * Stream raw, <b>pre-guardrail</b> tokens to observers via {@link AgentObserver#onToken}.
         * Off by default: those tokens are unguarded and unsafe to surface directly to a user. The
         * guarded result is always available from the returned {@link AgentResponse} and onTurnEnd.
         */
        public Builder streamRawTokens(boolean streamRawTokens) {
            this.streamRawTokens = streamRawTokens;
            return this;
        }

        public DefaultAgent build() {
            return new DefaultAgent(this);
        }
    }
}
