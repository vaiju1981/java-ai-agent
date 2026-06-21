package dev.vaijanath.aiagent.model;

/**
 * Generates a typed result directly from a model as schema-constrained JSON — so callers declare a
 * result type instead of writing a parser for free-text output.
 *
 * <p>Implementations (e.g. in the LangChain4j adapter) ask the provider for JSON matching {@code type}
 * and bind it with one generic mapper. {@code type} is typically a {@code record}.
 */
public interface StructuredOutput {

    <T> T generate(ModelRequest request, Class<T> type);
}
