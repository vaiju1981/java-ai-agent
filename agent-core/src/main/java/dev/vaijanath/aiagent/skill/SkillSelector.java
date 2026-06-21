package dev.vaijanath.aiagent.skill;

import java.util.List;

/** Chooses which skills are relevant to a task (so only their instructions enter context). */
public interface SkillSelector {

    List<Skill> select(SkillRegistry registry, String task);
}
