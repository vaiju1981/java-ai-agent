package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Asks a model which catalog skills apply to a task. The model sees only the compact catalog
 * (names + descriptions); the selected skills' full instructions are loaded afterwards.
 */
public final class LlmSkillSelector implements SkillSelector {

    private final ModelPort model;

    public LlmSkillSelector(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public List<Skill> select(SkillRegistry registry, String task) {
        String catalog = registry.catalog();
        if (catalog.isBlank()) {
            return List.of();
        }
        String prompt = "Given the available skills and a task, list the names of the skills that "
                + "would help, comma-separated. Reply 'none' if no skill applies.\n\n"
                + "Skills:\n" + catalog + "\nTask: " + task + "\n\nRelevant skill names:";
        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(prompt))));
        String text = resp.text() == null ? "" : resp.text().toLowerCase(Locale.ROOT);

        List<Skill> selected = new ArrayList<>();
        for (Skill s : registry.all()) {
            if (text.contains(s.name().toLowerCase(Locale.ROOT))) {
                selected.add(s);
            }
        }
        return selected;
    }
}
