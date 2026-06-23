package dev.vaijanath.aiagent.fincopilot.approval;

import dev.vaijanath.aiagent.tool.ApprovalHandler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires human-in-the-loop tool approval. The {@link ApprovalHandler} bean is discovered by the
 * agent-spring-boot-starter and applied to the agent, so an effectful tool the policy doesn't auto-approve
 * is routed to {@link SseApprovalHandler} (it blocks the streaming turn until the user resolves it).
 */
@Configuration
class ApprovalConfiguration {

    @Bean
    PendingApprovals pendingApprovals() {
        return new PendingApprovals();
    }

    @Bean
    ApprovalHandler approvalHandler(
            PendingApprovals pending,
            // Keep below the chat request timeout (90s) so the turn isn't killed by its deadline first.
            @Value("${fincopilot.approval-timeout:60s}") Duration approvalTimeout) {
        return new SseApprovalHandler(pending, approvalTimeout);
    }
}
