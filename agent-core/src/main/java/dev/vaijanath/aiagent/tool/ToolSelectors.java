package dev.vaijanath.aiagent.tool;

import java.util.List;
import java.util.Locale;

/** Common {@link ToolSelector} policies. */
public final class ToolSelectors {

    private ToolSelectors() {}

    /** Presents every tool (the default; fine for a handful of tools). */
    public static ToolSelector all() {
        return (task, available) -> available;
    }

    /**
     * Presents up to {@code max} tools whose name or description shares a word with the task — so an
     * agent with dozens of tools only shows the model the relevant few. Falls back to the first
     * {@code max} tools if nothing matches, so the agent is never left tool-less by accident.
     */
    public static ToolSelector keyword(int max) {
        return (task, available) -> {
            String t = task.toLowerCase(Locale.ROOT);
            List<Tool> matched = available.stream().filter(tool -> matches(t, tool)).limit(max).toList();
            return matched.isEmpty() ? available.stream().limit(max).toList() : matched;
        };
    }

    private static boolean matches(String task, Tool tool) {
        ToolSpec spec = tool.spec();
        String haystack = (spec.name() + " " + spec.description()).toLowerCase(Locale.ROOT);
        for (String word : haystack.split("\\W+")) {
            if (word.length() >= 4 && task.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
