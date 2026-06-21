package dev.vaijanath.aiagent.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Holds available skills and exposes a compact catalog (the progressive-disclosure surface). */
public final class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillRegistry register(Skill skill) {
        skills.put(skill.name(), skill);
        return this;
    }

    public Optional<Skill> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<Skill> all() {
        return List.copyOf(skills.values());
    }

    /** Compact "name: description" listing — the only thing shown before a skill is selected. */
    public String catalog() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : skills.values()) {
            sb.append(s.name()).append(": ").append(s.description()).append('\n');
        }
        return sb.toString();
    }
}
