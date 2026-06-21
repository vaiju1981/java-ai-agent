package dev.vaijanath.aiagent.tool;

import java.util.List;

/**
 * Chooses which of (potentially many) registered tools to present to the model for a given task.
 *
 * <p>Presenting every tool on every turn bloats context and makes the model's choice harder once
 * there are dozens of tools. A selector narrows them to the relevant few — progressive disclosure for
 * tools, mirroring {@code SkillSelector}. The default presents all tools.
 */
public interface ToolSelector {

    List<Tool> select(String task, List<Tool> available);
}
