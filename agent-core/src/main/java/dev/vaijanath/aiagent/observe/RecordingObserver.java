package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records a run's model responses <em>and</em> tool results, in order, so it can be replayed
 * deterministically: a {@code ReplayModelPort} replays the model responses and a
 * {@code ReplayToolExecutor} replays the tool results — without re-running the real tools.
 */
public final class RecordingObserver implements AgentObserver {

    private final List<ModelResponse> modelResponses = new CopyOnWriteArrayList<>();
    private final List<ToolResult> toolResults = new CopyOnWriteArrayList<>();

    @Override
    public void onModelResponse(ModelResponse response) {
        modelResponses.add(response);
    }

    @Override
    public void onToolResult(String toolName, ToolResult result) {
        toolResults.add(result);
    }

    public List<ModelResponse> modelResponses() {
        return List.copyOf(modelResponses);
    }

    public List<ToolResult> toolResults() {
        return List.copyOf(toolResults);
    }
}
