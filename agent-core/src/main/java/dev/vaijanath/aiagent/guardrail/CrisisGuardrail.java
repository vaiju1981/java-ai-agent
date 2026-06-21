package dev.vaijanath.aiagent.guardrail;

import java.util.List;
import java.util.Locale;

/**
 * Detects acute-distress / self-harm signals in user input and short-circuits with a warm,
 * supportive response instead of a normal model reply (the first stage of Mitra's safety pipeline).
 *
 * <p><b>Safety-critical:</b> the default response is intentionally generic. Real deployments MUST
 * supply a response with <em>verified, region-appropriate</em> crisis resources (e.g. the US 988
 * Suicide &amp; Crisis Lifeline), checked against official sources, before serving anyone.
 */
public final class CrisisGuardrail implements Guardrail {

    public static final List<String> DEFAULT_PHRASES = List.of(
            "kill myself", "want to die", "wanna die", "suicide", "end my life",
            "hurt myself", "self harm", "self-harm", "don't want to live", "dont want to live");

    public static final String DEFAULT_RESPONSE =
            "I'm really glad you told me, and I'm sorry you're going through something this hard. "
            + "You deserve support right now. Please reach out to someone who can help immediately — "
            + "a trusted adult, or your local emergency services or a crisis hotline in your area. "
            + "You don't have to face this alone.";

    private final List<String> phrases;
    private final String response;

    public CrisisGuardrail() {
        this(DEFAULT_PHRASES, DEFAULT_RESPONSE);
    }

    public CrisisGuardrail(List<String> phrases, String response) {
        this.phrases = phrases.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList();
        this.response = response;
    }

    @Override
    public GuardrailDecision check(GuardrailStage stage, String content) {
        if (stage != GuardrailStage.INPUT) {
            return GuardrailDecision.allow(content);
        }
        String haystack = content.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (haystack.contains(phrase)) {
                return GuardrailDecision.block(response, "crisis phrase detected");
            }
        }
        return GuardrailDecision.allow(content);
    }

    @Override
    public String name() {
        return "crisis";
    }
}
