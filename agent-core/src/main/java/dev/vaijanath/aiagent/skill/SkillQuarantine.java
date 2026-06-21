package dev.vaijanath.aiagent.skill;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Governs acquired skills, <b>isolated per tenant</b>. A synthesized skill is quarantined (pending)
 * with provenance and does not affect behavior until it is explicitly {@link #approve approved}, at
 * which point it is promoted into that tenant's active registry. Promotions are versioned, so a bad
 * skill can be {@link #rollback rolled back}. Nothing the model authors becomes active — for any
 * tenant — without passing through here, and a skill approved for one tenant is invisible to another.
 *
 * <p>Thread-safe: all mutating operations are synchronized.
 */
public final class SkillQuarantine {

    private record Key(String tenant, String name) {}

    private final Map<String, SkillRegistry> activeByTenant = new ConcurrentHashMap<>();
    private final Map<String, SkillCatalog> viewByTenant = new ConcurrentHashMap<>();
    private final Map<Key, PendingSkill> pending = new LinkedHashMap<>();
    private final Map<Key, Deque<Skill>> history = new HashMap<>();
    private final Map<Key, Integer> versions = new HashMap<>();

    /** The approved, active skills for {@code tenant} as a read-only catalog (no direct registration). */
    public synchronized SkillCatalog active(String tenant) {
        registry(tenant); // ensure it exists
        return viewByTenant.computeIfAbsent(tenant, t -> readOnly(registry(t)));
    }

    /** Convenience for the {@code "default"} tenant. */
    public SkillCatalog active() {
        return active("default");
    }

    /** Quarantine a candidate skill for a tenant with provenance; it stays pending until approved. */
    public synchronized PendingSkill submit(String tenant, Skill skill, String sourceTask, String author) {
        Key key = new Key(tenant, skill.name());
        int version = versions.getOrDefault(key, 0) + 1;
        PendingSkill candidate =
                new PendingSkill(skill, SkillProvenance.of(sourceTask, author, tenant, version));
        pending.put(key, candidate);
        return candidate;
    }

    /** All pending candidates across every tenant. */
    public synchronized List<PendingSkill> pending() {
        return List.copyOf(pending.values());
    }

    /** Pending candidates for one tenant. */
    public synchronized List<PendingSkill> pending(String tenant) {
        return pending.entrySet().stream()
                .filter(e -> e.getKey().tenant().equals(tenant))
                .map(Map.Entry::getValue)
                .toList();
    }

    public synchronized Optional<PendingSkill> pending(String tenant, String name) {
        return Optional.ofNullable(pending.get(new Key(tenant, name)));
    }

    /** Promote a tenant's pending skill into its active registry, keeping the prior version. */
    public synchronized Optional<Skill> approve(String tenant, String name) {
        Key key = new Key(tenant, name);
        PendingSkill candidate = pending.remove(key);
        if (candidate == null) {
            return Optional.empty();
        }
        SkillRegistry registry = registry(tenant);
        registry.get(name).ifPresent(prev -> history.computeIfAbsent(key, k -> new ArrayDeque<>()).push(prev));
        registry.register(candidate.skill());
        versions.put(key, candidate.provenance().version());
        return Optional.of(candidate.skill());
    }

    /** Discard a tenant's pending skill without activating it. */
    public synchronized boolean reject(String tenant, String name) {
        return pending.remove(new Key(tenant, name)) != null;
    }

    /** Roll a tenant's active skill back to its previous version, or remove it if there was none. */
    public synchronized boolean rollback(String tenant, String name) {
        Key key = new Key(tenant, name);
        Deque<Skill> prior = history.get(key);
        SkillRegistry registry = registry(tenant);
        if (prior != null && !prior.isEmpty()) {
            registry.register(prior.pop());
            return true;
        }
        return registry.remove(name);
    }

    private SkillRegistry registry(String tenant) {
        return activeByTenant.computeIfAbsent(tenant, t -> new SkillRegistry());
    }

    private static SkillCatalog readOnly(SkillCatalog delegate) {
        return new SkillCatalog() {
            @Override
            public Optional<Skill> get(String name) {
                return delegate.get(name);
            }

            @Override
            public List<Skill> all() {
                return delegate.all();
            }

            @Override
            public String catalog() {
                return delegate.catalog();
            }
        };
    }
}
