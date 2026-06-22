package dev.vaijanath.aiagent.guardrail;

import java.util.regex.Pattern;

/**
 * A transforming guardrail that redacts common PII before content reaches the model, persistence,
 * or the user. It never blocks — it allows the redacted text through.
 *
 * <p>A starter subset of Mitra's scrubber: emails, US SSNs, and phone numbers. Patterns are kept
 * conservative to avoid mangling ordinary text; broaden them per deployment.
 */
public final class PiiScrubGuardrail implements Guardrail {

    // Possessive quantifiers and bounded labels: no backtracking, so a long non-matching run (e.g.
    // 32 KB of "a") is scanned in linear — not quadratic — time. A greedy "+@" here is a ReDoS.
    private static final Pattern EMAIL =
            Pattern.compile("[\\w.+-]{1,64}+@[\\w-]{1,63}+(?:\\.[\\w-]{1,63}+)++");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern PHONE =
            Pattern.compile("\\b(?:\\+?\\d{1,3}[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b");

    @Override
    public GuardrailDecision check(GuardrailStage stage, String content) {
        String scrubbed = content;
        scrubbed = EMAIL.matcher(scrubbed).replaceAll("[email]");
        scrubbed = SSN.matcher(scrubbed).replaceAll("[ssn]"); // before PHONE: SSN is 3-2-4, phone is 3-3-4
        scrubbed = PHONE.matcher(scrubbed).replaceAll("[phone]");
        return GuardrailDecision.allow(scrubbed);
    }

    @Override
    public String name() {
        return "pii-scrub";
    }
}
