package dev.vaijanath.aiagent.deep;

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
    private volatile Status status = Status.PENDING;
    private volatile String result = "";

    public PlanStep(int index, String description) {
        this.index = index;
        this.description = Objects.requireNonNull(description, "description");
    }

    public int index() {
        return index;
    }

    public String description() {
        return description;
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
