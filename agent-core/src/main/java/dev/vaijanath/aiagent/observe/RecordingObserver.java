package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the model responses of a run, in order, so it can be replayed deterministically via
 * {@code ReplayModelPort}.
 */
public final class RecordingObserver implements AgentObserver {

    private final List<ModelResponse> modelResponses = new CopyOnWriteArrayList<>();

    @Override
    public void onModelResponse(ModelResponse response) {
        modelResponses.add(response);
    }

    public List<ModelResponse> modelResponses() {
        return List.copyOf(modelResponses);
    }
}
