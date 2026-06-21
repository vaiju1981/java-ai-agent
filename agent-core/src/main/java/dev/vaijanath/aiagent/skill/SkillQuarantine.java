package dev.vaijanath.aiagent.skill;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Governs acquired skills. A synthesized skill is <b>quarantined</b> (pending) with provenance and
 * does not affect behavior until it is explicitly {@link #approve approved}, at which point it is
 * promoted into the active {@link SkillRegistry}. Promotions are versioned, so a bad skill can be
 * {@link #rollback rolled back}. Nothing the model authors becomes active without passing through here.
 *
 * <p>Thread-safe: all mutating operations are synchronized.
 */
public final class SkillQuarantine {

    private final SkillRegistry active;
    private final Map<String, PendingSkill> pending = new LinkedHashMap<>();
    private final Map<String, Deque<Skill>> history = new HashMap<>();
    private final Map<String, Integer> versions = new HashMap<>();

    public SkillQuarantine(SkillRegistry active) {
        this.active = active;
    }

    /** The catalog of approved, active skills (what agents actually use). */
    public SkillRegistry active() {
        return active;
    }

    /** Quarantine a candidate skill with provenance; it stays pending until approved. */
    public synchronized PendingSkill submit(Skill skill, String sourceTask, String author, String tenant) {
        int version = versions.getOrDefault(skill.name(), 0) + 1;
        PendingSkill candidate =
                new PendingSkill(skill, SkillProvenance.of(sourceTask, author, tenant, version));
        pending.put(skill.name(), candidate);
        return candidate;
    }

    public synchronized List<PendingSkill> pending() {
        return List.copyOf(pending.values());
    }

    public synchronized Optional<PendingSkill> pending(String name) {
        return Optional.ofNullable(pending.get(name));
    }

    /** Promote a pending skill into the active registry, keeping the prior version for rollback. */
    public synchronized Optional<Skill> approve(String name) {
        PendingSkill candidate = pending.remove(name);
        if (candidate == null) {
            return Optional.empty();
        }
        active.get(name).ifPresent(prev -> history.computeIfAbsent(name, k -> new ArrayDeque<>()).push(prev));
        active.register(candidate.skill());
        versions.put(name, candidate.provenance().version());
        return Optional.of(candidate.skill());
    }

    /** Discard a pending skill without activating it. */
    public synchronized boolean reject(String name) {
        return pending.remove(name) != null;
    }

    /** Roll the active skill back to its previous version, or remove it if there was none. */
    public synchronized boolean rollback(String name) {
        Deque<Skill> prior = history.get(name);
        if (prior != null && !prior.isEmpty()) {
            active.register(prior.pop());
            return true;
        }
        return active.remove(name);
    }
}
