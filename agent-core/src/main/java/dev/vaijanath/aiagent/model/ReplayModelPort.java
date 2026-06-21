package dev.vaijanath.aiagent.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ModelPort} that replays previously recorded model responses in order. Combined with
 * {@code RecordingObserver} and a {@code ReplayToolExecutor}, this gives deterministic replay of a
 * run: the model's (nondeterministic) outputs are fixed and recorded tool results are returned
 * without re-running the real tools, so the loop reproduces with no repeated side effects.
 */
public final class ReplayModelPort implements ModelPort {

    private final List<ModelResponse> recorded;
    private final AtomicInteger index = new AtomicInteger();

    public ReplayModelPort(List<ModelResponse> recorded) {
        this.recorded = List.copyOf(recorded);
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        int i = index.getAndIncrement();
        if (i >= recorded.size()) {
            throw new IllegalStateException("replay exhausted: no recorded response for call #" + i);
        }
        return recorded.get(i);
    }

    @Override
    public String name() {
        return "replay";
    }
}
