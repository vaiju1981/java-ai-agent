package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.concurrent.atomic.AtomicLong;

/** Accumulates model-call count and token usage across a run (thread-safe for sub-agents). */
public final class TokenAccountingObserver implements AgentObserver {

    private final AtomicLong modelCalls = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    @Override
    public void onModelResponse(ModelResponse response) {
        modelCalls.incrementAndGet();
        inputTokens.addAndGet(response.usage().inputTokens());
        outputTokens.addAndGet(response.usage().outputTokens());
    }

    public long modelCalls() {
        return modelCalls.get();
    }

    public long inputTokens() {
        return inputTokens.get();
    }

    public long outputTokens() {
        return outputTokens.get();
    }

    public long totalTokens() {
        return inputTokens.get() + outputTokens.get();
    }
}
