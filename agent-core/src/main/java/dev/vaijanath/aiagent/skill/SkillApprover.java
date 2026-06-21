package dev.vaijanath.aiagent.skill;

/**
 * Decides whether a quarantined skill may be promoted to active. Plug in an evaluation, a human
 * review step, or a policy. The default is {@link #manual()} — nothing auto-activates.
 */
@FunctionalInterface
public interface SkillApprover {

    boolean approve(PendingSkill candidate);

    /** Never auto-approve: every acquired skill waits for an explicit out-of-band decision. */
    static SkillApprover manual() {
        return candidate -> false;
    }

    /** Approve everything — only for trusted or test contexts. */
    static SkillApprover acceptingAll() {
        return candidate -> true;
    }
}
