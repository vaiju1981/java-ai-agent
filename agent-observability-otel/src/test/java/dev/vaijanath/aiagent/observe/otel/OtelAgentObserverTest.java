package dev.vaijanath.aiagent.observe.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OtelAgentObserverTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final Tracer tracer =
            OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build().getTracer("test");

    @AfterEach
    void close() {
        tracerProvider.close();
    }

    private SpanData spanNamed(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no span named " + name));
    }

    @Test
    void emitsAgentTurnSpanWithTokenAttributes() {
        ModelPort model = request -> ModelResponse.text("hi", new Usage(7, 3));
        DefaultAgent.builder().model(model).observer(new OtelAgentObserver(tracer)).build()
                .run(new AgentRequest("hello"));

        SpanData span = spanNamed("agent.turn");
        assertEquals(7L, (long) span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(3L, (long) span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode()); // a normal completion is not an error
    }

    @Test
    void errorTurnGetsErrorStatusAndRecordsTheException() {
        ModelPort failing = request -> {
            throw new RuntimeException("boom");
        };
        DefaultAgent.builder().model(failing).observer(new OtelAgentObserver(tracer)).build()
                .run(new AgentRequest("x"));

        SpanData span = spanNamed("agent.turn");
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertTrue(
                span.getEvents().stream().anyMatch(e -> e.getName().equals("exception")),
                "the model exception is recorded on the span");
    }

    @Test
    void turnSpanNestsUnderAnEnclosingSpan() {
        ModelPort model = request -> ModelResponse.text("hi", new Usage(1, 1));
        Span parent = tracer.spanBuilder("http.request").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            DefaultAgent.builder().model(model).observer(new OtelAgentObserver(tracer)).build()
                    .run(new AgentRequest("hello"));
        } finally {
            parent.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(2, spans.size());
        assertEquals(
                spanNamed("http.request").getSpanId(),
                spanNamed("agent.turn").getParentSpanId(),
                "the turn span nests under the enclosing span");
    }
}
