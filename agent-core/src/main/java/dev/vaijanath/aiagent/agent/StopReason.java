package dev.vaijanath.aiagent.agent;

/**
 * A stable, machine-readable classification of how a turn ended — the typed view of
 * {@link AgentResponse#stopReason()} (a free-form string) plus its {@code blocked} flag. Clients can
 * branch on the {@link Category} and consult {@link #retryable()} instead of string-matching, and the
 * set is closed so a switch stays exhaustive.
 *
 * <p>{@link #UNKNOWN} covers any custom or future stop reason (e.g. a guardrail's own reason carried on
 * a non-blocked stop), so callers always get a defined value.
 */
public enum StopReason {

    /** The turn produced a genuine answer. */
    COMPLETED(Category.SUCCESS, false),
    /** A guardrail replaced the output (input or output policy). */
    BLOCKED(Category.BLOCKED, false),
    /** The step budget was exhausted before a final answer; retrying the same input rarely helps. */
    MAX_STEPS(Category.INCOMPLETE, false),
    /** The whole-turn deadline elapsed; may be transient under load. */
    DEADLINE_EXCEEDED(Category.TIMEOUT, true),
    /** The model call failed after retries; typically transient. */
    MODEL_ERROR(Category.ERROR, true),
    /** The turn hit its token budget — a cost ceiling, distinct from a model outage; not retryable. */
    BUDGET_EXCEEDED(Category.ERROR, false),
    /** Any other stop reason. */
    UNKNOWN(Category.ERROR, false);

    /** A coarse grouping for routing/alerting/HTTP-status decisions. */
    public enum Category {
        SUCCESS,
        BLOCKED,
        INCOMPLETE,
        TIMEOUT,
        ERROR
    }

    private final Category category;
    private final boolean retryable;

    StopReason(Category category, boolean retryable) {
        this.category = category;
        this.retryable = retryable;
    }

    public Category category() {
        return category;
    }

    /** Whether re-issuing the same request is reasonable (the failure is likely transient). */
    public boolean retryable() {
        return retryable;
    }

    /** Classifies a response: a blocked turn is {@link #BLOCKED}, otherwise the stop-reason string maps. */
    public static StopReason of(AgentResponse response) {
        if (response.blocked()) {
            return BLOCKED;
        }
        return switch (response.stopReason()) {
            case "completed" -> COMPLETED;
            case "max_steps" -> MAX_STEPS;
            case "deadline_exceeded" -> DEADLINE_EXCEEDED;
            case "model_error" -> MODEL_ERROR;
            case "budget_exceeded" -> BUDGET_EXCEEDED;
            default -> UNKNOWN;
        };
    }
}
