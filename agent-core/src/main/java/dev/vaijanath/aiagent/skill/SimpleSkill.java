package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;

/** A plain {@link Skill} value. */
public record SimpleSkill(String name, String description, String instructions, List<Tool> tools)
        implements Skill {

    public SimpleSkill {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
