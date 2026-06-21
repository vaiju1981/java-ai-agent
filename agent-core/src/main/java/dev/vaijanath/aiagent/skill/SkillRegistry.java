package dev.vaijanath.aiagent.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds available skills and exposes a compact catalog (the progressive-disclosure surface).
 * Thread-safe: all access is synchronized, so it can be shared across concurrent turns.
 */
public final class SkillRegistry implements SkillCatalog {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public synchronized SkillRegistry register(Skill skill) {
        skills.put(skill.name(), skill);
        return this;
    }

    /** Removes a skill, returning true if it was present (used by {@link SkillQuarantine} rollback). */
    public synchronized boolean remove(String name) {
        return skills.remove(name) != null;
    }

    @Override
    public synchronized Optional<Skill> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public synchronized List<Skill> all() {
        return List.copyOf(skills.values());
    }

    @Override
    public synchronized String catalog() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : skills.values()) {
            sb.append(s.name()).append(": ").append(s.description()).append('\n');
        }
        return sb.toString();
    }
}
