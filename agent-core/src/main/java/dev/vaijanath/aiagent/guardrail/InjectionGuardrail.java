package dev.vaijanath.aiagent.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects common prompt-injection attempts in input — instructions that try to override the agent's
 * own guidance ("ignore previous instructions", "reveal your system prompt", role-switch demands).
 * Runs on the {@link GuardrailStage#INPUT} stage and blocks with a safe replacement; output passes
 * through unchanged.
 *
 * <p>Because {@code RetrievalAugmentedAgent} weaves retrieved context into the input, this guardrail
 * also screens injection arriving through RAG content, not only direct user input. Patterns are
 * high-signal (multi-word, to limit false positives) and ReDoS-safe (possessive quantifiers); tune
 * them per deployment.
 */
public final class InjectionGuardrail implements Guardrail {

    private static final String SAFE_REPLACEMENT =
            "I can't act on instructions that try to override my guidelines. How else can I help?";

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s++(?:all\\s++)?(?:the\\s++)?(?:previous|prior|above)\\s++instructions"),
            Pattern.compile("(?i)disregard\\s++(?:all\\s++)?(?:the\\s++)?(?:previous|prior|above)"),
            Pattern.compile("(?i)forget\\s++(?:everything|all|your\\s++(?:instructions|rules|prompt))"),
            Pattern.compile("(?i)(?:reveal|show|print|repeat)\\s++(?:me\\s++)?your\\s++(?:system\\s++)?"
                    + "(?:prompt|instructions)"),
            Pattern.compile("(?i)you\\s++are\\s++now\\s++"),
            Pattern.compile("(?i)(?:new|updated)\\s++(?:system\\s++)?instructions\\s*+:"));

    @Override
    public GuardrailDecision check(GuardrailStage stage, String content) {
        if (stage != GuardrailStage.INPUT) {
            return GuardrailDecision.allow(content);
        }
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(content).find()) {
                return GuardrailDecision.block(SAFE_REPLACEMENT, "prompt_injection");
            }
        }
        return GuardrailDecision.allow(content);
    }

    @Override
    public String name() {
        return "injection";
    }
}
