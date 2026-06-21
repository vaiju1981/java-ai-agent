package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Asks a model which catalog skills apply to a task (it sees only the compact catalog).
 *
 * <p>Prefer the {@link StructuredOutput} constructor: the chosen names are bound from JSON to
 * {@link Selection} (no parsing). The {@link ModelPort} constructor is a portable fallback that
 * matches names in a free-text reply.
 */
public final class LlmSkillSelector implements SkillSelector {

    /** Structured selection bound from JSON. */
    public record Selection(List<String> skillNames) {
    }

    private final StructuredOutput structured;
    private final ModelPort model;

    public LlmSkillSelector(StructuredOutput structured) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.model = null;
    }

    public LlmSkillSelector(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
        this.structured = null;
    }

    @Override
    public List<Skill> select(SkillCatalog registry, String task) {
        String catalog = registry.catalog();
        if (catalog.isBlank()) {
            return List.of();
        }
        String prompt = "Given the available skills and a task, choose which skills would help.\n\n"
                + "Skills:\n" + catalog + "\nTask: " + task;

        if (structured != null) {
            Selection selection =
                    structured.generate(ModelRequest.of(List.of(Message.user(prompt))), Selection.class);
            List<Skill> out = new ArrayList<>();
            if (selection != null && selection.skillNames() != null) {
                for (String n : selection.skillNames()) {
                    if (n != null) {
                        registry.get(n.strip()).ifPresent(out::add);
                    }
                }
            }
            return out;
        }

        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(
                prompt + "\n\nReply with the relevant skill names, comma-separated, or 'none'."))));
        String text = resp.text() == null ? "" : resp.text().toLowerCase(Locale.ROOT);
        List<Skill> out = new ArrayList<>();
        for (Skill s : registry.all()) {
            if (text.contains(s.name().toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }
}
