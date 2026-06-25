package dev.vaijanath.aiagent.supervise;

import java.util.List;
import java.util.Map;

/**
 * The brain of a {@link ManagerAgent}: given the task, the rounds completed so far, and the roster of
 * available specialists, decide the next move — delegate one more step to a specialist, or finish
 * with the final answer.
 *
 * <p>This is the iterative cousin of {@link Router}. A {@code Router} chooses once and the supervisor
 * returns that specialist's reply; a {@code Manager} keeps deciding round after round, so it can
 * delegate, read the result, and re-delegate (the delegate &rarr; critique &rarr; re-delegate
 * pattern). Putting the intelligence here keeps {@link ManagerAgent} a small, predictable loop.
 *
 * <p>Pluggable like {@link Router} and {@code Planner}: heuristic, model-based ({@link LlmManager}),
 * or a fixed script in a test.
 */
@FunctionalInterface
public interface Manager {

    /**
     * Decide the next move.
     *
     * @param task    the original request to the manager
     * @param history the delegations completed so far, oldest first (empty on the first call)
     * @param roster  the available specialists as {@code name -> description}
     */
    Decision decide(String task, List<Round> history, Map<String, String> roster);

    /** One completed step: the specialist that ran, the instruction it was given, and what it returned. */
    record Round(String specialist, String instruction, String output) {
    }

    /**
     * The manager's choice for the next move: either {@link #delegate} to a specialist, or
     * {@link #finish} with the final answer. Modeled as a record with factory methods in the same
     * style as {@code Reflection} and {@link dev.vaijanath.aiagent.agent.AgentResponse}.
     */
    record Decision(boolean done, String specialist, String instruction, String answer) {

        /** Run {@code specialist} with {@code instruction}, then decide again next round. */
        public static Decision delegate(String specialist, String instruction) {
            return new Decision(false, specialist, instruction, null);
        }

        /** Stop now; {@code answer} is the final output of the whole manager turn. */
        public static Decision finish(String answer) {
            return new Decision(true, null, null, answer);
        }
    }
}
