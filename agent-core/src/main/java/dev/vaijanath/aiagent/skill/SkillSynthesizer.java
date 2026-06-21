package dev.vaijanath.aiagent.skill;

/**
 * Distills a reusable {@link Skill} from a task and a good solution. Returns {@code null} when no
 * worthwhile skill can be extracted.
 */
public interface SkillSynthesizer {

    Skill synthesize(String task, String solution);
}
