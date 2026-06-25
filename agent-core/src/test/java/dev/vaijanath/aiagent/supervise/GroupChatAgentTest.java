package dev.vaijanath.aiagent.supervise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GroupChatAgentTest {

    /** A selector that plays a fixed list of speakers, then ends the chat. */
    private static SpeakerSelector scripted(String... speakers) {
        AtomicInteger i = new AtomicInteger();
        return (task, transcript, roster) -> {
            int idx = i.getAndIncrement();
            return idx < speakers.length ? speakers[idx] : null;
        };
    }

    @Test
    void roundRobinCyclesThroughSpeakers() {
        List<String> spoke = new CopyOnWriteArrayList<>();
        Agent alice = req -> {
            spoke.add("alice");
            return AgentResponse.completed("alice msg");
        };
        Agent bob = req -> {
            spoke.add("bob");
            return AgentResponse.completed("bob msg");
        };

        AgentResponse r = GroupChatAgent.builder()
                .agent("alice", "first", alice)
                .agent("bob", "second", bob)
                .selector(new RoundRobinSelector())
                .maxRounds(4)
                .build()
                .run(new AgentRequest("discuss"));

        assertEquals(List.of("alice", "bob", "alice", "bob"), spoke);
        assertEquals("max_rounds", r.stopReason());
        assertEquals("bob msg", r.output()); // the last message is the answer
    }

    @Test
    void eachSpeakerSeesTheSharedTranscript() {
        List<String> bobSaw = new CopyOnWriteArrayList<>();
        Agent alice = req -> AgentResponse.completed("the sky is blue");
        Agent bob = req -> {
            bobSaw.add(req.input());
            return AgentResponse.completed("agreed");
        };

        GroupChatAgent.builder()
                .agent("alice", "a", alice)
                .agent("bob", "b", bob)
                .selector(scripted("alice", "bob"))
                .build()
                .run(new AgentRequest("what color is the sky?"));

        assertTrue(bobSaw.get(0).contains("what color is the sky?"), bobSaw.get(0)); // the task
        assertTrue(bobSaw.get(0).contains("the sky is blue"), bobSaw.get(0)); // alice's prior turn
    }

    @Test
    void endsWhenTheSelectorReturnsNoSpeaker() {
        Agent only = req -> AgentResponse.completed("final answer");

        AgentResponse r = GroupChatAgent.builder()
                .agent("a", "x", only)
                .selector(scripted("a"))
                .build()
                .run(new AgentRequest("q"));

        assertTrue(r.isCompleted());
        assertEquals("final answer", r.output());
    }

    @Test
    void aBlockedTurnShortCircuits() {
        Agent blocker = req -> AgentResponse.blocked("not allowed", "guardrail");

        AgentResponse r = GroupChatAgent.builder()
                .agent("a", "x", blocker)
                .selector((task, transcript, roster) -> "a")
                .maxRounds(3)
                .build()
                .run(new AgentRequest("q"));

        assertTrue(r.blocked());
        assertEquals("not allowed", r.output());
    }

    @Test
    void rejectsNoAgents() {
        assertThrows(IllegalArgumentException.class,
                () -> GroupChatAgent.builder().selector((t, tr, ro) -> null).build());
    }
}
