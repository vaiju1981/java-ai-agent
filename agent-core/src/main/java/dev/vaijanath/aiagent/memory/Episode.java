package dev.vaijanath.aiagent.memory;

/**
 * A past experience an agent can learn from: what it was asked, what it produced, whether that
 * succeeded, and the lesson to apply next time.
 */
public record Episode(String task, String outcome, boolean success, String lesson) {
}
