package dev.vaijanath.aiagent.fincopilot.approval;

import dev.vaijanath.aiagent.tool.ApprovalHandler;
import dev.vaijanath.aiagent.tool.ApprovalRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ApprovalHandler} for FinCopilot's streaming chat. The runtime calls it on the turn's virtual
 * thread when an effectful tool needs approval; it blocks on a {@link PendingApprovals} future until the
 * user resolves it (via {@code POST /api/chat/approve}) or the timeout elapses. The runtime has already
 * notified the SSE observer ({@code onApprovalRequired} → an {@code approval_required} event), so the
 * client knows to prompt. A timeout (or any failure) declines the call rather than hanging the turn.
 */
public final class SseApprovalHandler implements ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(SseApprovalHandler.class);

    private final PendingApprovals pending;
    private final Duration timeout;

    public SseApprovalHandler(PendingApprovals pending, Duration timeout) {
        this.pending = pending;
        this.timeout = timeout;
    }

    @Override
    public boolean requestApproval(ApprovalRequest request) {
        CompletableFuture<Boolean> future = pending.register(request.approvalId(), request.context().principal());
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("approval {} timed out after {}", request.approvalId(), timeout);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.warn("approval {} failed", request.approvalId(), e.getCause());
            return false;
        } finally {
            pending.forget(request.approvalId());
        }
    }
}
