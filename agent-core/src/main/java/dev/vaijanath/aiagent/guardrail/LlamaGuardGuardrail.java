package dev.vaijanath.aiagent.guardrail;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A safety guardrail backed by a Llama Guard classifier model (e.g. Ollama's {@code llama-guard3:1b}),
 * reached through a {@link ModelPort} — so it runs fully locally and on any substrate.
 *
 * <p>The classifier returns {@code "safe"} or {@code "unsafe\n<categories>"}; unsafe content is
 * blocked with a safe replacement. If the classifier itself fails, the guardrail fails <b>closed</b>
 * by default (it blocks and says the safety check was unavailable) rather than silently letting
 * unclassified content through — set {@code failOpen} to invert that for non-critical uses.
 */
public final class LlamaGuardGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(LlamaGuardGuardrail.class);

    public static final String DEFAULT_REPLACEMENT =
            "I'm not able to help with that. Let's talk about something else.";

    private final ModelPort classifier;
    private final String replacement;
    private final boolean failOpen;

    public LlamaGuardGuardrail(ModelPort classifier) {
        this(classifier, DEFAULT_REPLACEMENT, false);
    }

    public LlamaGuardGuardrail(ModelPort classifier, String replacement, boolean failOpen) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
        this.failOpen = failOpen;
    }

    @Override
    public GuardrailDecision check(GuardrailStage stage, String content) {
        // Classify the content in the role it will play, so Llama Guard judges it in context.
        Message message =
                (stage == GuardrailStage.INPUT) ? Message.user(content) : Message.assistant(content);
        try {
            ModelResponse resp = classifier.chat(ModelRequest.of(List.of(message)));
            String raw = resp.text() == null ? "" : resp.text().strip();
            String verdict = raw.toLowerCase(Locale.ROOT);
            if (verdict.startsWith("safe")) {
                return GuardrailDecision.allow(content);
            }
            if (verdict.startsWith("unsafe")) {
                String categories = raw.substring("unsafe".length()).strip(); // keep original case (e.g. S1)
                log.info("llama-guard flagged {} content: {}",
                        stage, categories.isBlank() ? "(unspecified)" : categories);
                return GuardrailDecision.block(replacement,
                        "llama-guard:" + stage + (categories.isBlank() ? "" : ":" + categories));
            }
            // Malformed/empty verdict (neither "safe" nor "unsafe") = inconclusive → fail closed.
            log.warn("llama-guard returned an inconclusive verdict at {} (failOpen={}): '{}'",
                    stage, failOpen, verdict);
            return failOpen
                    ? GuardrailDecision.allow(content)
                    : GuardrailDecision.block(replacement, "safety check inconclusive at " + stage);
        } catch (RuntimeException e) {
            log.warn("llama-guard classification failed at {} (failOpen={})", stage, failOpen, e);
            return failOpen
                    ? GuardrailDecision.allow(content)
                    : GuardrailDecision.block(replacement, "safety check unavailable at " + stage);
        }
    }

    @Override
    public String name() {
        return "llama-guard";
    }
}
