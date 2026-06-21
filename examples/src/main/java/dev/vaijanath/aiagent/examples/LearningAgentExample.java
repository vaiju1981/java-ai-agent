package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.learn.Reflection;
import dev.vaijanath.aiagent.learn.Reflector;
import dev.vaijanath.aiagent.learn.ReflectiveAgent;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import java.util.function.Supplier;

/**
 * Rung 6 — an agent that <b>learns from its mistakes</b>.
 *
 * <p>This is deterministic and offline so the behavior is unmistakable: a worker that gets the
 * answer wrong until the correct fact is in front of it, and a reviewer that knows better. The
 * {@link ReflectiveAgent} self-corrects within a run (records the lesson, retries with it) and —
 * sharing one {@link EpisodicStore} — applies that lesson on a later run without any retry.
 *
 * <p>To make it autonomous with a real LLM, swap {@code WORKER} for a {@code DefaultAgent} backed by
 * a model and {@code REVIEWER} for an {@code LlmReflector}.
 */
public final class LearningAgentExample {

    // Gets it wrong ("Sydney") until the correct fact ("Canberra") appears in the input.
    private static final Supplier<Agent> WORKER = () -> request ->
            request.input().contains("Canberra")
                    ? AgentResponse.completed("The capital of Australia is Canberra.")
                    : AgentResponse.completed("The capital of Australia is Sydney.");

    // A reviewer that recognizes the mistake and teaches the lesson.
    private static final Reflector REVIEWER = (task, answer) ->
            answer.contains("Canberra")
                    ? Reflection.ok()
                    : Reflection.issue("The capital of Australia is Canberra, not Sydney.");

    public static void main(String[] args) {
        EpisodicStore memory = new InMemoryEpisodicStore();

        System.out.println("== Learning from mistakes ==\n");

        System.out.println("--- Run 1: no prior experience, allowed to retry ---");
        Agent run1 = ReflectiveAgent.builder()
                .worker(WORKER).reflector(REVIEWER).memory(memory).maxAttempts(2)
                .build();
        System.out.println("answer: " + run1.run(new AgentRequest("What is the capital of Australia?")).output());
        System.out.println("lessons now remembered:");
        memory.recall("capital of Australia", 5).forEach(e -> System.out.println("  • " + e.lesson()));

        System.out.println("\n--- Run 2: same memory, NO retries allowed ---");
        Agent run2 = ReflectiveAgent.builder()
                .worker(WORKER).reflector(REVIEWER).memory(memory).maxAttempts(1)
                .build();
        System.out.println("answer: " + run2.run(new AgentRequest("Remind me — the capital of Australia?")).output());
        System.out.println("\n(Run 2 was right on the FIRST attempt: it recalled Run 1's lesson.)");
    }
}
