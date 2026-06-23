package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ResilientModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.observe.RedactingObserver;
import dev.vaijanath.aiagent.tool.ApprovalHandler;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprover;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Opinionated production assembly for the governed runtime. Unlike ad-hoc builder composition, it
 * requires durable/session storage, audit delivery, and argument validation; enables model/tool
 * timeouts, deny-effectful authorization, result framing/capping, and redacted telemetry; and places
 * one hard-deadline policy boundary around the finished agent.
 */
public final class ProductionAgentRuntime implements Agent {

    private final Agent delegate;

    private ProductionAgentRuntime(Builder b) {
        Objects.requireNonNull(b.model, "model");
        Objects.requireNonNull(b.conversationStore, "conversationStore");
        Objects.requireNonNull(b.auditSink, "auditSink");
        Objects.requireNonNull(b.argumentValidator, "argumentValidator");
        requirePositive(b.modelTimeout, "modelTimeout");
        requirePositive(b.toolTimeout, "toolTimeout");
        if (b.modelAttempts < 1 || b.maxToolResultChars < 1 || b.maxSteps < 1) {
            throw new IllegalArgumentException(
                    "modelAttempts, maxToolResultChars, and maxSteps must be positive");
        }

        for (Tool tool : b.tools) {
            b.argumentValidator.validateSchema(tool.spec()).ifPresent(error -> {
                throw new IllegalArgumentException("invalid schema for tool '" + tool.name() + "': " + error);
            });
        }

        ModelPort resilient = new ResilientModelPort(
                b.model, b.modelAttempts, b.modelTimeout, b.modelBackoff.toMillis());
        DefaultAgent.Builder core = DefaultAgent.builder()
                .model(resilient)
                .conversationStore(b.conversationStore)
                .auditSink(b.auditSink)
                .turnAudit(false)
                .toolApprover(b.toolApprover)
                .toolArgumentValidator(b.argumentValidator)
                .toolTimeout(b.toolTimeout)
                .maxToolResultChars(b.maxToolResultChars)
                .frameToolResults(true)
                .maxSteps(b.maxSteps)
                .systemPrompt(b.systemPrompt);
        if (b.approvalHandler != null) {
            core.approvalHandler(b.approvalHandler);
        }
        b.tools.forEach(core::tool);
        // Content policy runs inside the conversation transaction so only governed input/output is
        // persisted. The outer seam below owns the hard deadline and lifecycle audit only.
        b.guardrails.forEach(core::guardrail);
        b.observers.forEach(o -> core.observer(new RedactingObserver(o)));
        b.rawObservers.forEach(core::observer);
        this.delegate = Trust.govern(core.build(), b.auditSink, List.of());
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        return delegate.run(request);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ModelPort model;
        private ConversationStore conversationStore;
        private AuditSink auditSink;
        private ToolArgumentValidator argumentValidator;
        private ToolApprover toolApprover = ToolApprovers.denyEffectful();
        private ApprovalHandler approvalHandler;
        private final List<Tool> tools = new ArrayList<>();
        private final List<Guardrail> guardrails = new ArrayList<>();
        private final List<AgentObserver> observers = new ArrayList<>();
        private final List<AgentObserver> rawObservers = new ArrayList<>();
        private Duration modelTimeout = Duration.ofSeconds(60);
        private Duration modelBackoff = Duration.ofMillis(500);
        private Duration toolTimeout = Duration.ofSeconds(30);
        private int modelAttempts = 3;
        private int maxToolResultChars = 8192;
        private int maxSteps = 8;
        private String systemPrompt;

        public Builder model(ModelPort model) { this.model = model; return this; }
        public Builder conversationStore(ConversationStore store) { this.conversationStore = store; return this; }
        public Builder auditSink(AuditSink sink) { this.auditSink = sink; return this; }
        public Builder argumentValidator(ToolArgumentValidator validator) { this.argumentValidator = validator; return this; }
        public Builder toolApprover(ToolApprover approver) { this.toolApprover = approver; return this; }
        /** Escalate an unapproved effectful tool to a human approver instead of hard-denying it. */
        public Builder approvalHandler(ApprovalHandler handler) { this.approvalHandler = handler; return this; }
        public Builder tool(Tool tool) { this.tools.add(tool); return this; }
        public Builder guardrail(Guardrail guardrail) { this.guardrails.add(guardrail); return this; }
        /** Content is redacted before this observer sees it. */
        public Builder observer(AgentObserver observer) { this.observers.add(observer); return this; }
        /** Explicit opt-in for record/replay or another protected content-bearing sink. */
        public Builder rawObserver(AgentObserver observer) { this.rawObservers.add(observer); return this; }
        public Builder systemPrompt(String prompt) { this.systemPrompt = prompt; return this; }
        public Builder modelTimeout(Duration timeout) { this.modelTimeout = timeout; return this; }
        public Builder modelAttempts(int attempts) { this.modelAttempts = attempts; return this; }
        public Builder modelBackoff(Duration backoff) { this.modelBackoff = backoff; return this; }
        public Builder toolTimeout(Duration timeout) { this.toolTimeout = timeout; return this; }
        public Builder maxToolResultChars(int max) { this.maxToolResultChars = max; return this; }
        public Builder maxSteps(int max) { this.maxSteps = max; return this; }

        public ProductionAgentRuntime build() { return new ProductionAgentRuntime(this); }
    }
}
