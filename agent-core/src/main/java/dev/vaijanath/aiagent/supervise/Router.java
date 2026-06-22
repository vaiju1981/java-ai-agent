package dev.vaijanath.aiagent.supervise;

import java.util.Map;

/**
 * Chooses which specialist should handle a request. Given the user input and the available agents
 * (name &rarr; description), it returns the chosen name. {@link SupervisorAgent} falls back to a
 * default when the returned name is not a known specialist, so a router may be heuristic, model-based,
 * or approximate without risking an unhandled request.
 */
@FunctionalInterface
public interface Router {

    String route(String input, Map<String, String> agents);
}
