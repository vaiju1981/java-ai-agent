package dev.vaijanath.aiagent.guardrail;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A minimal reference guardrail: blocks any content containing a configured keyword
 * (case-insensitive). It is a placeholder for the real {@code kidguard} pipeline, and is handy for
 * tests and demos.
 */
public final class KeywordBlocklistGuardrail implements Guardrail {

    private final List<String> blocked;
    private final String replacement;

    public KeywordBlocklistGuardrail(List<String> blockedKeywords, String replacement) {
        this.blocked = blockedKeywords.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    @Override
    public GuardrailDecision check(GuardrailStage stage, String content) {
        String haystack = content.toLowerCase(Locale.ROOT);
        for (String word : blocked) {
            if (haystack.contains(word)) {
                return GuardrailDecision.block(
                        replacement, "matched blocked keyword at " + stage + ": " + word);
            }
        }
        return GuardrailDecision.allow(content);
    }

    @Override
    public String name() {
        return "keyword-blocklist";
    }
}
