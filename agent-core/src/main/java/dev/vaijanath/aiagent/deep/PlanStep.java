package dev.vaijanath.aiagent.deep;

import java.util.List;
import java.util.Objects;

/** One subtask in a {@link Plan}. Mutable status/result so it doubles as a live todo item. */
public final class PlanStep {

    public enum Status {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }

    private final int index;
    private final String description;
    private final List<Integer> dependsOn;
    private volatile Status status = Status.PENDING;
    private volatile String result = "";

    /** An independent subtask (no dependencies) — the flat-plan default. */
    public PlanStep(int index, String description) {
        this(index, description, List.of());
    }

    /**
     * A subtask that depends on earlier steps. {@code dependsOn} holds the {@link #index()} values
     * whose results must be ready before this step runs; a {@link DeepAgent} feeds those results in.
     */
    public PlanStep(int index, String description, List<Integer> dependsOn) {
        this.index = index;
        this.description = Objects.requireNonNull(description, "description");
        this.dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public int index() {
        return index;
    }

    public String description() {
        return description;
    }

    /** Indices of the steps this one depends on (empty when independent). */
    public List<Integer> dependsOn() {
        return dependsOn;
    }

    public Status status() {
        return status;
    }

    public void status(Status status) {
        this.status = status;
    }

    public String result() {
        return result;
    }

    public void result(String result) {
        this.result = result == null ? "" : result;
    }
}
