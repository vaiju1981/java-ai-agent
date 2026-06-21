package dev.vaijanath.aiagent.deep;

/** Decomposes a task into a {@link Plan} of subtasks. Pluggable: LLM-based, GOAP, fixed, etc. */
public interface Planner {

    Plan plan(String task);
}
