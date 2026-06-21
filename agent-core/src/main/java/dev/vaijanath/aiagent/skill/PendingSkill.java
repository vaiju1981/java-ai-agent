package dev.vaijanath.aiagent.skill;

/** A skill awaiting approval in the {@link SkillQuarantine}, carrying its {@link SkillProvenance}. */
public record PendingSkill(Skill skill, SkillProvenance provenance) {

    public String name() {
        return skill.name();
    }
}
