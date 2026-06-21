package dev.vaijanath.aiagent.eval;

import java.util.Locale;
import java.util.function.Predicate;

/** One evaluation case: an input and a check on the agent's output. */
public record EvalCase(String name, String input, Predicate<String> passes) {

    /** A case that passes when the output contains {@code expected} (case-insensitive). */
    public static EvalCase contains(String name, String input, String expected) {
        String needle = expected.toLowerCase(Locale.ROOT);
        return new EvalCase(name, input,
                out -> out != null && out.toLowerCase(Locale.ROOT).contains(needle));
    }
}
