package dev.vaijanath.aiagent.model;

/**
 * The L0 seam: a minimal abstraction over a chat model.
 *
 * <p>The runtime depends only on this, never on a concrete substrate, so the same agent can run on
 * LangChain4j, Spring AI, Google ADK, or a local model by swapping the implementation.
 */
public interface ModelPort {

    ModelResponse chat(ModelRequest request);

    /** A short identifier for logs/traces (e.g. {@code "ollama:llama3.2"}). */
    default String name() {
        return getClass().getSimpleName();
    }
}
