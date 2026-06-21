package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.learn.Reflection;
import dev.vaijanath.aiagent.learn.ReflectiveAgent;
import dev.vaijanath.aiagent.learn.Reflector;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import java.util.function.Supplier;

/**
 * Real learning from real mistakes. A <b>real model</b> is asked for a tagline, but a hidden
 * requirement — it must include the app's name, "Mitra" — is revealed only by the reviewer's
 * feedback. The model can't satisfy it on the first try (it doesn't know the name), so you reliably
 * see the loop: a first attempt is rejected, the {@link ReflectiveAgent} records the lesson, and the
 * retry applies it. The INFO log shows the rejected attempt; the lesson is a stored {@code Episode}.
 * Run with {@code AGENT_MODEL}.
 */
public final class LearningAgentExample {

    private static final String APP_NAME = "Mitra";

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        System.out.println("== LearningAgentExample ==  model: " + model.name());
        if (model instanceof StubModelPort) {
            System.out.println("(NOTE: set AGENT_MODEL to a real Ollama model to see genuine self-correction.)");
        }
        System.out.println();

        // Hidden requirement, surfaced only as feedback: the tagline must name the app.
        Reflector reviewer = (task, answer) -> answer.contains(APP_NAME)
                ? Reflection.ok()
                : Reflection.issue("The tagline must include our app's name, which is '" + APP_NAME + "'.");

        EpisodicStore memory = new InMemoryEpisodicStore();
        Supplier<Agent> worker = () -> DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a marketing copywriter. Reply with only the tagline.")
                .build();
        Agent agent = ReflectiveAgent.builder()
                .worker(worker).reflector(reviewer).memory(memory).maxAttempts(3)
                .build();

        String task = "Write a short, catchy one-line tagline for our new app.";
        System.out.println("> " + task);
        System.out.println("  (hidden review rule: the tagline must mention the app name '" + APP_NAME + "')\n");

        String answer = agent.run(new AgentRequest(task)).output();

        System.out.println("final tagline: " + answer);
        System.out.println("mentions the app name: " + answer.contains(APP_NAME));
        System.out.println("\nlessons the agent learned (recorded episodes):");
        memory.recall(task, 5).forEach(e -> System.out.println("  • " + e.lesson()));
    }
}
