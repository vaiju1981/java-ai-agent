package dev.vaijanath.aiagent.skill;

import java.util.List;
import java.util.Optional;

/**
 * A read-only view of available skills — what selectors and agents see. It deliberately has no
 * {@code register}, so a governed registry can be exposed (e.g. by {@link SkillQuarantine}) without
 * letting callers add skills that bypassed approval.
 */
public interface SkillCatalog {

    Optional<Skill> get(String name);

    List<Skill> all();

    /** Compact "name: description" listing — the only thing shown before a skill is selected. */
    String catalog();
}
