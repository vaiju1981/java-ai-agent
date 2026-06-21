package dev.vaijanath.aiagent.memory;

/**
 * A past experience an agent can learn from: the tenant it belongs to, what it was asked, what it
 * produced, whether that succeeded, and the lesson to apply next time. The tenant scopes recall, so a
 * lesson learned for one tenant is never surfaced to another.
 */
public record Episode(String tenant, String task, String outcome, boolean success, String lesson) {

    public Episode {
        tenant = (tenant == null || tenant.isBlank()) ? "default" : tenant;
    }

    /** Back-compat: an episode for the {@code "default"} tenant. */
    public Episode(String task, String outcome, boolean success, String lesson) {
        this("default", task, outcome, success, lesson);
    }
}
