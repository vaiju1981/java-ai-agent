package dev.vaijanath.aiagent.tool;

/**
 * A tool's capability class, so the runtime can reason about risk.
 *
 * <ul>
 *   <li>{@link #READ_ONLY} — only reads or computes; safe to run without special authorization.</li>
 *   <li>{@link #EFFECTFUL} — can change state, spend money, or reach the outside world; gate it.</li>
 * </ul>
 *
 * <p>A tool of unspecified effect is treated as {@code EFFECTFUL} — the safe default.
 */
public enum ToolEffect {
    READ_ONLY,
    EFFECTFUL
}
