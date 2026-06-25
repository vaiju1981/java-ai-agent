package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates model-call count and token usage across a run (thread-safe for sub-agents). The aggregate
 * counters answer "how much did this run cost in tokens"; {@link #tokensByModel()} breaks the same total
 * down per model, so a multi-model setup (e.g. a supervisor on a large model, workers on a small one) can
 * see where the spend went. Pair the per-model breakdown with a {@link dev.vaijanath.aiagent.model.Pricing}
 * table to turn tokens into cost.
 */
public final class TokenAccountingObserver implements AgentObserver {

    private final AtomicLong modelCalls = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final Map<String, Usage> byModel = new ConcurrentHashMap<>();

    @Override
    public void onModelResponse(ModelResponse response) {
        modelCalls.incrementAndGet();
        inputTokens.addAndGet(response.usage().inputTokens());
        outputTokens.addAndGet(response.usage().outputTokens());
    }

    @Override
    public void onUsage(String model, Usage usage) {
        byModel.merge(model, usage, Usage::plus);
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

    /** A snapshot of token usage per model name; empty if the run was driven without {@code onUsage}. */
    public Map<String, Usage> tokensByModel() {
        return Map.copyOf(byModel);
    }
}
