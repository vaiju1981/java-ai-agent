package dev.vaijanath.aiagent.observe.otel;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Objects;

/**
 * Maps {@link AgentObserver} events to OpenTelemetry spans: one {@code agent.turn} span per turn,
 * with model/tool/guardrail steps as span events and token usage as attributes (following the
 * {@code gen_ai.*} semantic-convention naming).
 *
 * <p>The turn span is made <em>current</em> for its duration, so it nests under an enclosing span (e.g.
 * an incoming HTTP server span) and synchronous sub-agent turns nest under it. A failed or blocked turn
 * gets {@code ERROR} status and any exception is recorded, so error traces surface in the usual way.
 * Span state is held per-thread, so concurrent (sub-)agents each get their own span. Only the
 * OpenTelemetry <em>API</em> is required; the consumer supplies the SDK and exporter.
 */
public final class OtelAgentObserver implements AgentObserver {

    private final Tracer tracer;
    private final ThreadLocal<Span> currentSpan = new ThreadLocal<>();
    private final ThreadLocal<Scope> currentScope = new ThreadLocal<>();
    private final ThreadLocal<long[]> tokens = ThreadLocal.withInitial(() -> new long[2]);

    public OtelAgentObserver(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    @Override
    public void onTurnStart(String input) {
        Span span = tracer.spanBuilder("agent.turn").startSpan();
        currentSpan.set(span);
        currentScope.set(span.makeCurrent()); // nest under any enclosing span; children nest under this
        tokens.set(new long[2]);
    }

    @Override
    public void onGuardrail(GuardrailStage stage, String guardrailName, GuardrailDecision decision) {
        Span span = currentSpan.get();
        if (span != null) {
            span.addEvent("guardrail." + (decision.allowed() ? "allow" : "block")
                    + ":" + guardrailName + ":" + stage);
        }
    }

    @Override
    public void onModelResponse(ModelResponse response) {
        long[] t = tokens.get();
        t[0] += response.usage().inputTokens();
        t[1] += response.usage().outputTokens();
        Span span = currentSpan.get();
        if (span != null) {
            span.addEvent("model.response");
        }
    }

    @Override
    public void onToolCall(ToolCall call) {
        Span span = currentSpan.get();
        if (span != null) {
            span.addEvent("tool.call:" + call.name());
        }
    }

    /** A recoverable failure (e.g. the model call threw): record it and mark the span as errored. */
    @Override
    public void onError(String stage, Throwable error) {
        Span span = currentSpan.get();
        if (span != null) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, stage);
        }
    }

    @Override
    public void onTurnEnd(AgentResponse response) {
        Span span = currentSpan.get();
        if (span != null) {
            long[] t = tokens.get();
            span.setAttribute("gen_ai.usage.input_tokens", t[0]);
            span.setAttribute("gen_ai.usage.output_tokens", t[1]);
            span.setAttribute("agent.blocked", response.blocked());
            span.setAttribute("agent.stop_reason", response.stopReason());
            // A non-completion that isn't a deliberate guardrail block (model_error, deadline_exceeded,
            // max_steps) is an error, so it shows up under the usual trace error filter.
            if (!response.blocked() && !"completed".equals(response.stopReason())) {
                span.setStatus(StatusCode.ERROR, response.stopReason());
            }
            Scope scope = currentScope.get();
            if (scope != null) {
                scope.close();
            }
            span.end();
        }
        currentSpan.remove();
        currentScope.remove();
        tokens.remove();
    }
}
