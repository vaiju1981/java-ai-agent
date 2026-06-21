package dev.vaijanath.aiagent.guardrail;

import dev.vaijanath.aiagent.model.ModelPort;
import java.util.List;

/** Convenience factories for common guardrail pipelines. */
public final class Guardrails {

    private Guardrails() {}

    /**
     * The "kidguard" pipeline, in order: crisis short-circuit → PII scrub → Llama Guard classifier.
     * Add the returned guardrails to an agent in order; the first to block wins, transforms chain.
     *
     * @param guardModel a {@link ModelPort} backed by a Llama Guard model (e.g. local
     *     {@code llama-guard3:1b}). Review {@link CrisisGuardrail}'s resources before production use.
     */
    public static List<Guardrail> kidguard(ModelPort guardModel) {
        return List.of(
                new CrisisGuardrail(),
                new PiiScrubGuardrail(),
                new LlamaGuardGuardrail(guardModel));
    }
}
