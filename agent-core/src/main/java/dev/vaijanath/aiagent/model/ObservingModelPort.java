package dev.vaijanath.aiagent.model;

import dev.vaijanath.aiagent.observe.AgentObserver;
import java.util.List;
import java.util.Objects;

/**
 * Decorates any {@link ModelPort} so each call emits {@code onModelCall} / {@code onModelResponse}
 * to observers — useful for metering or tracing model calls that happen outside the {@code DefaultAgent}
 * loop (e.g. a {@code DeepAgent}'s planner and synthesizer). Observer failures are isolated.
 */
public final class ObservingModelPort implements ModelPort {

    private final ModelPort delegate;
    private final List<AgentObserver> observers;

    public ObservingModelPort(ModelPort delegate, List<AgentObserver> observers) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.observers = List.copyOf(observers);
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        for (AgentObserver o : observers) {
            safe(() -> o.onModelCall(request));
        }
        ModelResponse response = delegate.chat(request);
        for (AgentObserver o : observers) {
            safe(() -> o.onModelResponse(response));
        }
        return response;
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // observers must never break a model call
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }
}
