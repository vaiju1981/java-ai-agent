package dev.vaijanath.aiagent.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Pricing;
import dev.vaijanath.aiagent.model.TokenPrice;
import dev.vaijanath.aiagent.model.Usage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PerModelUsageTest {

    /** A model that always answers with fixed token usage and reports a fixed {@link ModelPort#name()}. */
    private static ModelPort named(String name, Usage usage) {
        return new ModelPort() {
            @Override
            public ModelResponse chat(ModelRequest request) {
                return ModelResponse.text("ok", usage);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    void attributesUsageToTheModelThatProducedIt() {
        TokenAccountingObserver acct = new TokenAccountingObserver();
        DefaultAgent.builder()
                .model(named("big-model", new Usage(100, 50)))
                .observer(acct)
                .build()
                .run(new AgentRequest("hi"));

        assertEquals(1, acct.modelCalls());
        assertEquals(150, acct.totalTokens());
        assertEquals(Map.of("big-model", new Usage(100, 50)), acct.tokensByModel());
    }

    @Test
    void breaksSpendDownAcrossModelsSharingOneObserver() {
        // The multi-model payoff: a supervisor on a big model + workers on a small one, one shared meter.
        TokenAccountingObserver acct = new TokenAccountingObserver();
        Agent big = DefaultAgent.builder()
                .model(named("big", new Usage(100, 50)))
                .observer(acct)
                .build();
        Agent small = DefaultAgent.builder()
                .model(named("small", new Usage(10, 5)))
                .observer(acct)
                .build();

        big.run(new AgentRequest("a"));
        small.run(new AgentRequest("b"));
        small.run(new AgentRequest("c"));

        Map<String, Usage> byModel = acct.tokensByModel();
        assertEquals(new Usage(100, 50), byModel.get("big"));
        assertEquals(new Usage(20, 10), byModel.get("small")); // two calls accumulate
        assertEquals(3, acct.modelCalls());
        assertEquals(180, acct.totalTokens());

        // BYO pricing turns the per-model breakdown into cost; the unpriced "small" model is free.
        Pricing pricing = new Pricing(Map.of("big", new TokenPrice(3.00, 15.00)));
        assertEquals(0.00105, pricing.total(byModel), 1e-9); // 100/1e6*3 + 50/1e6*15
    }

    @Test
    void unknownUsageStillTagsTheModelWithoutInventingTokens() {
        TokenAccountingObserver acct = new TokenAccountingObserver();
        DefaultAgent.builder()
                .model(named("quiet", Usage.UNKNOWN)) // provider reported no counts
                .observer(acct)
                .build()
                .run(new AgentRequest("hi"));

        assertEquals(Map.of("quiet", Usage.UNKNOWN), acct.tokensByModel());
        assertEquals(0, acct.totalTokens());
        assertEquals(1, acct.modelCalls());
    }
}
