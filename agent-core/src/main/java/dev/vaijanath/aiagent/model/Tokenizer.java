package dev.vaijanath.aiagent.model;

/**
 * Estimates the token count of a string — so prompts and memory can be bounded by tokens rather than
 * characters or message count. Implementations may wrap a provider's exact tokenizer;
 * {@link HeuristicTokenizer} is a dependency-free approximation.
 */
@FunctionalInterface
public interface Tokenizer {

    int countTokens(String text);
}
