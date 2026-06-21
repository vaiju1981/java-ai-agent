package dev.vaijanath.aiagent.eval;

/** The outcome of one {@link EvalCase}. */
public record EvalResult(String name, boolean passed, String output) {
}
