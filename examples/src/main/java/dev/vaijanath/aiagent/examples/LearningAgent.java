package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.learn.LlmReflector;
import dev.vaijanath.aiagent.learn.Reflection;
import dev.vaijanath.aiagent.learn.ReflectiveAgent;
import dev.vaijanath.aiagent.learn.Reflector;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A self-learning agent: it learns from a mistake and recalls the lesson on a later, similar task.
 *
 * <p>{@link ReflectiveAgent} recalls lessons from past episodes, injects them, self-critiques the answer,
 * and on a poor answer records the lesson and retries with it applied. To keep this runnable with no model
 * or network, the worker and the critic here are <b>deterministic</b> — but the wiring is exactly what you
 * use with a real model (swap the worker for your agent and the critic for {@link LlmReflector}).
 *
 * <pre>{@code
 * ./gradlew :examples:run -PmainClass=dev.vaijanath.aiagent.examples.LearningAgent
 * }</pre>
 *
 * <p>For learning that survives restarts and is shared across instances, swap the {@link EpisodicStore}
 * for the durable, semantic {@code JdbcEpisodicStore} (agent-store-jdbc).
 */
public final class LearningAgent {

    private LearningAgent() {}

    public static void main(String[] args) {
        AtomicInteger workerCalls = new AtomicInteger();

        // A worker that "slips" by default, but applies any lesson it is handed (the lesson mentions Mitra).
        Agent worker = request -> {
            workerCalls.incrementAndGet();
            String reply = "Here is your summary.";
            if (request.input().contains("Mitra")) {
                reply += " — Mitra";
            }
            return AgentResponse.completed(reply);
        };

        // A deterministic critic: a reply must be signed "— Mitra", else it teaches that lesson.
        Reflector critic = (task, answer) ->
                answer.contains("— Mitra") ? Reflection.ok() : Reflection.issue("sign the reply as \"— Mitra\"");

        EpisodicStore memory = new InMemoryEpisodicStore(); // swap for JdbcEpisodicStore to persist + scale
        Agent learner = ReflectiveAgent.builder()
                .worker(() -> worker)
                .reflector(critic)
                .memory(memory)
                .maxAttempts(3)
                .build();

        // First task: no prior experience — it slips once, learns the lesson, and self-corrects.
        int before = workerCalls.get();
        String first = learner.run(new AgentRequest("Summarize the meeting notes.")).output();
        System.out.printf("task 1 -> %s   (%d attempts: slipped, then self-corrected)%n",
                first, workerCalls.get() - before);

        // Second, similar task: the lesson is recalled up front, so it gets it right on the first attempt.
        before = workerCalls.get();
        String second = learner.run(new AgentRequest("Summarize today's standup.")).output();
        System.out.printf("task 2 -> %s   (%d attempt: lesson recalled from the past)%n",
                second, workerCalls.get() - before);
    }
}
