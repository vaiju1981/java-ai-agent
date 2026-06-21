package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;

/**
 * A packaged capability (Anthropic-style): discovery metadata ({@link #name}, {@link #description}),
 * detailed {@link #instructions} loaded only when the skill is selected (progressive disclosure),
 * and optional {@link #tools}.
 */
public interface Skill {

    String name();

    String description();

    String instructions();

    default List<Tool> tools() {
        return List.of();
    }

    static Skill of(String name, String description, String instructions, List<Tool> tools) {
        return new SimpleSkill(name, description, instructions, List.copyOf(tools));
    }

    static Skill of(String name, String description, String instructions) {
        return new SimpleSkill(name, description, instructions, List.of());
    }
}
