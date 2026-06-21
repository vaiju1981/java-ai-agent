package dev.vaijanath.aiagent.observe.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

class OtelAgentObserverTest {

    @Test
    void emitsAgentTurnSpanWithTokenAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        Tracer tracer = sdk.getTracer("test");

        ModelPort model = request -> ModelResponse.text("hi", new Usage(7, 3));
        DefaultAgent.builder()
                .model(model)
                .observer(new OtelAgentObserver(tracer))
                .build()
                .run(new AgentRequest("hello"));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("agent.turn", span.getName());
        assertEquals(7L, (long) span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertEquals(3L, (long) span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));

        tracerProvider.close();
    }
}
