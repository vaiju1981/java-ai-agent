package dev.vaijanath.aiagent.fincopilot.approval;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rendezvous between an in-flight turn waiting on a human and the HTTP request that carries the
 * decision. A turn awaiting approval {@link #register}s a future keyed by {@code approvalId}; the approve
 * endpoint {@link #resolve}s it. Entries are bounded: the awaiting turn always {@link #forget}s its entry
 * when it stops waiting, and a resolve removes it.
 */
public final class PendingApprovals {

    private record Pending(String principal, CompletableFuture<Boolean> future) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /** Registers a pending approval and returns the future the awaiting turn blocks on. */
    public CompletableFuture<Boolean> register(String approvalId, String principal) {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(principal, "principal");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(approvalId, new Pending(principal, future));
        return future;
    }

    /** Drops a pending entry (called by the awaiting turn once it stops waiting, success or timeout). */
    public void forget(String approvalId) {
        pending.remove(approvalId);
    }

    /**
     * Resolves a pending approval if it exists and belongs to {@code principal} (so a user can only act on
     * their own approvals). Returns true if a waiting turn was resolved.
     */
    public boolean resolve(String approvalId, String principal, boolean approved) {
        Pending entry = pending.get(approvalId);
        if (entry == null || !entry.principal().equals(principal)) {
            return false;
        }
        pending.remove(approvalId);
        return entry.future().complete(approved);
    }
}
