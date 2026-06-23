package dev.vaijanath.aiagent.fincopilot.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PendingApprovalsTest {

    @Test
    void resolveCompletesTheFutureForTheOwner() throws Exception {
        PendingApprovals pending = new PendingApprovals();
        CompletableFuture<Boolean> future = pending.register("a1", "alice");

        assertTrue(pending.resolve("a1", "alice", true));
        assertTrue(future.get());
    }

    @Test
    void resolveRejectsAnotherUsersApproval() {
        PendingApprovals pending = new PendingApprovals();
        CompletableFuture<Boolean> future = pending.register("a1", "alice");

        assertFalse(pending.resolve("a1", "mallory", true), "a different user must not resolve it");
        assertFalse(future.isDone(), "the future stays pending");
    }

    @Test
    void resolveUnknownApprovalReturnsFalse() {
        assertFalse(new PendingApprovals().resolve("missing", "alice", true));
    }

    @Test
    void forgottenApprovalCannotBeResolved() {
        PendingApprovals pending = new PendingApprovals();
        pending.register("a1", "alice");
        pending.forget("a1");

        assertFalse(pending.resolve("a1", "alice", true));
    }
}
