package dev.vaijanath.aiagent.deep;

import java.util.Map;
import java.util.Optional;

/**
 * A deep agent's shared scratchpad — a small virtual filesystem for intermediate artifacts
 * (the plan, each subtask's result). Implementations must be safe for concurrent writes, since
 * sub-agents run in parallel.
 */
public interface Workspace {

    void write(String path, String content);

    Optional<String> read(String path);

    Map<String, String> files();
}
