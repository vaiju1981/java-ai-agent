package dev.vaijanath.aiagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalAugmentedAgentTest {

    // An agent that echoes whatever input it received, so we can inspect the augmented prompt.
    private static final Agent ECHO = request -> AgentResponse.completed(request.input());

    @Test
    void prependsRetrievedContextToThePrompt() {
        Retriever retriever = (tenant, query, limit) ->
                List.of(new RetrievedChunk("d1", "Paris is the capital of France", 0.9));

        AgentResponse response = new RetrievalAugmentedAgent(ECHO, retriever)
                .run(new AgentRequest("what is the capital of France?"));

        assertTrue(response.output().contains("Paris is the capital of France"), response.output());
        assertTrue(response.output().contains("what is the capital of France?"), response.output());
    }

    @Test
    void delegatesUnchangedWhenNothingRetrieved() {
        Retriever empty = (tenant, query, limit) -> List.of();

        AgentResponse response =
                new RetrievalAugmentedAgent(ECHO, empty).run(new AgentRequest("hello"));

        assertEquals("hello", response.output());
    }

    @Test
    void passesTenantAndTopKToTheRetriever() {
        int[] capturedLimit = {0};
        String[] capturedTenant = {null};
        Retriever spy = (tenant, query, limit) -> {
            capturedTenant[0] = tenant;
            capturedLimit[0] = limit;
            return List.of();
        };

        new RetrievalAugmentedAgent(ECHO, spy, 3).run(new AgentRequest("q"));

        assertEquals(3, capturedLimit[0]);
        assertEquals("default", capturedTenant[0], "ephemeral requests use the default tenant");
    }
}
