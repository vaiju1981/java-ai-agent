package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class ManagerAgentTest {

    private static final Agent RESEARCHER = req -> AgentResponse.completed("found: " + req.input());
    private static final Agent WRITER = req -> AgentResponse.completed("wrote: " + req.input());

    private static ManagerAgent.Builder team(Manager manager) {
        return ManagerAgent.builder()
                .specialist("researcher", "gathers facts", RESEARCHER)
                .specialist("writer", "drafts prose", WRITER)
                .manager(manager);
    }

    /** A manager that plays a fixed list of decisions in order. */
    private static Manager script(Manager.Decision... moves) {
        Queue<Manager.Decision> queue = new ArrayDeque<>(List.of(moves));
        return (task, history, roster) -> queue.poll();
    }

    @Test
    void loopsDelegatingAcrossSpecialistsThenFinishes() {
        Manager m = script(
                Manager.Decision.delegate("researcher", "look up X"),
                Manager.Decision.delegate("writer", "write it up"),
                Manager.Decision.finish("done: report ready"));

        AgentResponse r = team(m).build().run(new AgentRequest("produce a report"));

        assertTrue(r.isCompleted());
        assertEquals("done: report ready", r.output());
    }

    @Test
    void finishWithoutAnswerReturnsTheLastSpecialistOutput() {
        Manager m = script(
                Manager.Decision.delegate("writer", "draft"),
                Manager.Decision.finish(null));

        AgentResponse r = team(m).build().run(new AgentRequest("x"));

        assertEquals("wrote: draft", r.output());
    }

    @Test
    void managerSeesTheAccumulatingHistory() {
        List<Integer> sizesSeen = new ArrayList<>();
        Manager m = (task, history, roster) -> {
            sizesSeen.add(history.size());
            return history.size() < 2
                    ? Manager.Decision.delegate("researcher", "step " + history.size())
                    : Manager.Decision.finish("ok");
        };

        team(m).build().run(new AgentRequest("x"));

        assertEquals(List.of(0, 1, 2), sizesSeen);
    }

    @Test
    void managerReceivesTheRoster() {
        Manager m = (task, history, roster) -> {
            assertEquals("gathers facts", roster.get("researcher"));
            assertEquals("drafts prose", roster.get("writer"));
            return Manager.Decision.finish("ok");
        };

        AgentResponse r = team(m).build().run(new AgentRequest("x"));

        assertEquals("ok", r.output());
    }

    @Test
    void stopsAtTheRoundBudgetWhenNeverFinished() {
        Manager alwaysDelegate =
                (task, history, roster) -> Manager.Decision.delegate("researcher", "again");

        AgentResponse r = team(alwaysDelegate).maxRounds(3).build().run(new AgentRequest("x"));

        assertFalse(r.isCompleted());
        assertEquals("max_rounds", r.stopReason());
        assertEquals("found: again", r.output());
    }

    @Test
    void unknownSpecialistFallsBackToTheFirstRegistered() {
        Manager m = script(
                Manager.Decision.delegate("ghost", "do it"),
                Manager.Decision.finish(null));

        // fallback defaults to the first registered specialist (researcher)
        AgentResponse r = team(m).build().run(new AgentRequest("x"));

        assertEquals("found: do it", r.output());
    }

    @Test
    void specialistsRunInChildSessionsNotTheParentSession() {
        List<String> sessionsSeen = new ArrayList<>();
        Agent capture = req -> {
            sessionsSeen.add(req.context().sessionId());
            return AgentResponse.completed("ok");
        };
        Manager m = script(
                Manager.Decision.delegate("c", "one"),
                Manager.Decision.finish("end"));

        ManagerAgent.builder().specialist("c", "captures the session", capture).manager(m).build()
                .run(new AgentRequest("task", RequestContext.session("parent-session")));

        assertEquals(1, sessionsSeen.size());
        assertFalse(sessionsSeen.contains("parent-session"));
    }

    @Test
    void carriesPrincipalAndTenantIntoEachDelegation() {
        List<String> principals = new ArrayList<>();
        Agent capture = req -> {
            principals.add(req.context().principal());
            return AgentResponse.completed("ok");
        };
        Manager m = script(
                Manager.Decision.delegate("c", "one"),
                Manager.Decision.finish("end"));
        RequestContext ctx = new RequestContext("s1", "alice", "acme", "trace-1", null, Map.of());

        ManagerAgent.builder().specialist("c", "captures identity", capture).manager(m).build()
                .run(new AgentRequest("task", ctx));

        assertEquals(List.of("alice"), principals);
    }

    @Test
    void rejectsNoSpecialists() {
        assertThrows(IllegalArgumentException.class,
                () -> ManagerAgent.builder().manager((t, h, r) -> Manager.Decision.finish("x")).build());
    }

    @Test
    void rejectsAnUnknownFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> team((t, h, r) -> Manager.Decision.finish("x")).fallback("ghost").build());
    }

    @Test
    void rejectsANullDecisionFromTheManager() {
        Manager returnsNull = (task, history, roster) -> null;
        assertThrows(NullPointerException.class,
                () -> team(returnsNull).build().run(new AgentRequest("x")));
    }
}
