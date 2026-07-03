package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncidentResponseWarRoomTest {

    @TempDir
    Path tempDir;

    @Test
    void demoProducesGovernedIncidentArtifacts() throws Exception {
        Path audit = tempDir.resolve("incident.audit");

        IncidentResponseWarRoom.DemoRun run = IncidentResponseWarRoom.runDemo(audit);

        assertTrue(run.output().contains("SEV-1 checkout/payment degradation"));
        assertTrue(run.output().contains("tokenization-provider-v2"));
        assertFalse(run.output().contains("4111-1111-1111-1111"));
        assertTrue(run.workspace().containsKey("plan.md"));
        assertTrue(run.workspace().containsKey("step-1.txt"));
        assertTrue(run.workspace().containsKey("step-4.txt"));
        assertTrue(Files.size(audit) > 0, "governed run should write an audit trail");
    }
}
