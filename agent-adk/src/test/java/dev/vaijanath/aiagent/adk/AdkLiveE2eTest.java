package dev.vaijanath.aiagent.adk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.adk.agents.LlmAgent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A live, credential-gated end-to-end test: it builds a real Gemini-backed ADK {@link LlmAgent},
 * wraps it with {@link AdkAgent}, and drives one real turn through the ADK runner — exercising the
 * live {@code run()} path (session creation, event-stream draining, final-text reduction) that
 * {@link AdkAgentTest} can only cover up to the reduction step.
 *
 * <p>Runs only when {@code GOOGLE_API_KEY} is set, so it is skipped in CI and on local runs without
 * credentials. The model can be overridden via {@code ADK_TEST_MODEL} (default {@code gemini-2.0-flash}).
 */
class AdkLiveE2eTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
    void runsOneLiveTurnThroughAdk() {
        String model = System.getenv().getOrDefault("ADK_TEST_MODEL", "gemini-2.0-flash");
        LlmAgent agent = LlmAgent.builder()
                .name("e2e_probe")
                .model(model)
                .instruction("You are a test probe. Reply with a single word.")
                .build();

        AgentResponse response = new AdkAgent(agent).run(new AgentRequest("Say the word: pong"));

        assertNotNull(response.output());
        assertFalse(response.output().isBlank(), "a live ADK turn should return non-empty text");
    }
}
