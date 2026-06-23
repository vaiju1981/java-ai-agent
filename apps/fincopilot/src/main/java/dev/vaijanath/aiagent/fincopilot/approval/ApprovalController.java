package dev.vaijanath.aiagent.fincopilot.approval;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves a human-in-the-loop tool approval. A streaming turn that proposed an effectful tool is blocked
 * awaiting this call; it carries the {@code approvalId} from the {@code approval_required} SSE event. The
 * decision is scoped to the caller, so a user can only resolve their own pending approvals.
 */
@RestController
@RequestMapping("/api/chat")
class ApprovalController {

    private final PendingApprovals pending;

    ApprovalController(PendingApprovals pending) {
        this.pending = pending;
    }

    @PostMapping("/approve")
    ResponseEntity<Void> approve(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal,
            @RequestBody(required = false) ApprovalDecision body) {
        if (body == null || body.approvalId() == null || body.approvalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvalId is required");
        }
        boolean resolved = pending.resolve(body.approvalId(), principal, body.approved());
        // 404 if there's no matching pending approval for this user (unknown, already resolved, or expired).
        return resolved ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    record ApprovalDecision(String approvalId, boolean approved) {}
}
