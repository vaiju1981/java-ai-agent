package dev.vaijanath.aiagent.skill;

import java.time.Instant;

/**
 * Where an acquired skill came from — so a learned capability is auditable, not anonymous.
 *
 * @param sourceTask the task whose successful solution produced the skill
 * @param author     who authored it (e.g. {@code "model"} for a synthesized skill)
 * @param tenant     the tenant the acquisition happened under
 * @param acquiredAt when it was proposed
 * @param version    monotonically increasing per skill name, so promotions can be rolled back
 */
public record SkillProvenance(String sourceTask, String author, String tenant, Instant acquiredAt, int version) {

    public static SkillProvenance of(String sourceTask, String author, String tenant, int version) {
        return new SkillProvenance(sourceTask, author, tenant, Instant.now(), version);
    }
}
