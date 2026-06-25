package dev.vaijanath.aiagent.supervise;

import java.util.Map;

/**
 * Decides, after the active agent has produced an output, whether control should transfer to a peer —
 * the Swarm-style handoff. Returns the peer's name to transfer to, or the {@code current} name (or
 * blank/unknown) to stop, in which case the active agent's output is the answer.
 *
 * <p>Pluggable like {@link Router}: heuristic, model-based ({@link LlmHandoff}), or scripted in a test.
 * Where a {@code Router} chooses once up front, a {@code Handoff} is consulted after every hop, so
 * control can move laterally between peers until one keeps it.
 */
@FunctionalInterface
public interface Handoff {

    /**
     * @param task       the original request
     * @param current    the agent that just produced {@code lastOutput}
     * @param lastOutput what {@code current} returned this hop
     * @param roster     the available agents as {@code name -> description}
     * @return the peer to transfer to, or {@code current} (or blank/unknown) to stop
     */
    String next(String task, String current, String lastOutput, Map<String, String> roster);
}
