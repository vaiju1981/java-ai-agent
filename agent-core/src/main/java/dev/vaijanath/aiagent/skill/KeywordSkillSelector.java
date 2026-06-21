package dev.vaijanath.aiagent.skill;

import java.util.List;
import java.util.Locale;

/**
 * A deterministic, offline selector: picks skills whose name or description shares a word with the
 * task. Useful for tests and for keeping a skilled agent fully local.
 */
public final class KeywordSkillSelector implements SkillSelector {

    @Override
    public List<Skill> select(SkillRegistry registry, String task) {
        String t = task.toLowerCase(Locale.ROOT);
        return registry.all().stream()
                .filter(s -> matches(t, s))
                .toList();
    }

    private static boolean matches(String task, Skill skill) {
        String haystack = (skill.name() + " " + skill.description()).toLowerCase(Locale.ROOT);
        for (String word : haystack.split("\\W+")) {
            if (word.length() >= 4 && task.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
