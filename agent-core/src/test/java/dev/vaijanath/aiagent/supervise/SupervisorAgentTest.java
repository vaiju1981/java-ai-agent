package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisorAgentTest {

    private static final Agent WEATHER = request -> AgentResponse.completed("weather: sunny");
    private static final Agent BILLING = request -> AgentResponse.completed("billing: $10");

    private static SupervisorAgent.Builder twoSpecialists(Router router) {
        return SupervisorAgent.builder()
                .specialist("weather", "weather and forecasts", WEATHER)
                .specialist("billing", "billing and payments", BILLING)
                .router(router);
    }

    @Test
    void routesToTheChosenSpecialist() {
        Router router = (input, agents) -> input.contains("bill") ? "billing" : "weather";
        SupervisorAgent sup = twoSpecialists(router).build();

        assertEquals("billing: $10", sup.run(new AgentRequest("my bill is wrong")).output());
        assertEquals("weather: sunny", sup.run(new AgentRequest("will it rain?")).output());
    }

    @Test
    void unknownRouteFallsBackToFirstByDefault() {
        SupervisorAgent sup = twoSpecialists((input, agents) -> "does-not-exist").build();
        assertEquals("weather: sunny", sup.run(new AgentRequest("x")).output());
    }

    @Test
    void honorsAnExplicitFallback() {
        SupervisorAgent sup = twoSpecialists((input, agents) -> "nope").fallback("billing").build();
        assertEquals("billing: $10", sup.run(new AgentRequest("x")).output());
    }

    @Test
    void routerReceivesNamesAndDescriptions() {
        Map<String, String> captured = new HashMap<>();
        Router spy = (input, agents) -> {
            captured.putAll(agents);
            return "weather";
        };
        twoSpecialists(spy).build().run(new AgentRequest("x"));

        assertEquals("weather and forecasts", captured.get("weather"));
        assertEquals("billing and payments", captured.get("billing"));
    }

    @Test
    void rejectsNoSpecialistsOrUnknownFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> SupervisorAgent.builder().router((i, a) -> "x").build());
        assertThrows(IllegalArgumentException.class,
                () -> twoSpecialists((i, a) -> "weather").fallback("ghost").build());
    }
}
