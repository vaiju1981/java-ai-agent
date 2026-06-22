package dev.vaijanath.aiagent.structured;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs an {@link Agent} turn and coerces its free-text answer into a typed {@code T} via
 * {@link StructuredOutput} — so an agent can use tools to reach an answer and still hand back a
 * validated object instead of a string the caller must parse.
 *
 * <p>Two model calls by design: the wrapped agent's tool-calling loop produces the answer, then a
 * focused second call expresses that answer as {@code T}. This keeps the two concerns separate — the
 * model never has to choose between calling a tool and emitting final JSON in the same step, which is
 * where single-call structured-tool-calling schemes get unreliable.
 *
 * <p>Coercion runs only on a genuine completion. A blocked or stopped turn returns a
 * {@link StructuredResult} with a {@code null} value and the raw response (see {@link AgentResponse}),
 * so a guardrail's safe replacement is never force-fit into your schema. A completed answer that
 * cannot be coerced after {@code maxAttempts} raises {@link StructuredCoercionException} rather than
 * returning a fabricated object.
 */
public final class StructuredAgent {

    private static final Logger log = LoggerFactory.getLogger(StructuredAgent.class);

    private static final String DEFAULT_INSTRUCTION =
            "Express the assistant's answer below as the requested structured type. "
                    + "Use only information present in the answer; do not invent values.";

    private final Agent agent;
    private final StructuredOutput structuredOutput;
    private final String instruction;
    private final int maxAttempts;

    /** Coerce with the default instruction and one repair retry (two attempts total). */
    public StructuredAgent(Agent agent, StructuredOutput structuredOutput) {
        this(agent, structuredOutput, DEFAULT_INSTRUCTION, 2);
    }

    /**
     * @param instruction the system instruction for the coercion call
     * @param maxAttempts how many times to attempt coercion before failing (clamped to at least 1)
     */
    public StructuredAgent(
            Agent agent, StructuredOutput structuredOutput, String instruction, int maxAttempts) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.structuredOutput = Objects.requireNonNull(structuredOutput, "structuredOutput");
        this.instruction = Objects.requireNonNull(instruction, "instruction");
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** Convenience: a one-off turn in a fresh session, coerced to {@code type}. */
    public <T> StructuredResult<T> run(String input, Class<T> type) {
        return run(new AgentRequest(input), type);
    }

    public <T> StructuredResult<T> run(AgentRequest request, Class<T> type) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(type, "type");

        AgentResponse response = agent.run(request);
        if (!response.isCompleted()) {
            // Blocked or stopped — surface the raw response; don't coerce a safe replacement.
            return new StructuredResult<>(null, response);
        }

        ModelRequest coercion = ModelRequest.of(List.of(
                Message.system(instruction),
                Message.user("User request:\n" + request.input()
                        + "\n\nAssistant answer:\n" + response.output())));

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T value = structuredOutput.generate(coercion, type);
                return new StructuredResult<>(
                        Objects.requireNonNull(value, "structured value was null"), response);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("structured coercion attempt {}/{} failed: {}", attempt, maxAttempts, e.toString());
            }
        }
        throw new StructuredCoercionException(
                "could not coerce the answer into " + type.getSimpleName()
                        + " after " + maxAttempts + " attempt(s)",
                lastFailure);
    }
}
