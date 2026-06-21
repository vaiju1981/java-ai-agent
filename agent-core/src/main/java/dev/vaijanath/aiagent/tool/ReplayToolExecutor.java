package dev.vaijanath.aiagent.tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replays previously recorded tool results in order <b>without invoking the real tools</b>, so a
 * recorded run reproduces deterministically and side effects (writes, payments, emails) are not
 * repeated. Pair with {@code ReplayModelPort} (replays model responses) and a
 * {@code RecordingObserver} (captures both).
 */
public final class ReplayToolExecutor implements ToolExecutor {

    private final List<ToolResult> recorded;
    private final AtomicInteger index = new AtomicInteger();

    public ReplayToolExecutor(List<ToolResult> recorded) {
        this.recorded = List.copyOf(recorded);
    }

    @Override
    public ToolResult execute(String toolName, String argumentsJson) {
        int i = index.getAndIncrement();
        if (i >= recorded.size()) {
            return ToolResult.error("replay exhausted: no recorded tool result #" + i);
        }
        return recorded.get(i);
    }
}
