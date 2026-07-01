package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ControlledOperationsCopilotTest {

    @Test
    void demoShowsReplayAndEffectfulToolControl() {
        ControlledOperationsCopilot.DemoRun run = ControlledOperationsCopilot.runDemo();

        assertTrue(run.decision().contains("approve a $35 courtesy credit"));
        assertEquals(run.decision(), run.replayedDecision());
        assertTrue(run.refundAttempt().contains("Refund not issued"));
        assertTrue(run.approvedRefund().contains("Refund issued once"));
        assertEquals(3, run.readOnlyDbToolInvocations(), "replay should not re-run read-only DB tools");
        assertEquals(2, run.effectfulDbToolInvocations(), "two approved attempts should reach the DB tool");
        assertEquals(1, run.persistedRefunds(), "idempotency should collapse duplicate refund attempts");
        assertEquals(2, run.modelCalls());
        assertTrue(run.totalTokens() > 0);
        assertEquals(2, run.evalReport().passed());
    }
}
