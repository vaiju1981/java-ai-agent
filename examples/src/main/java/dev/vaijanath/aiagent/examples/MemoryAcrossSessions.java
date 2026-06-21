package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.learn.Reflection;
import dev.vaijanath.aiagent.learn.ReflectiveAgent;
import dev.vaijanath.aiagent.learn.Reflector;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.FileEpisodicStore;
import dev.vaijanath.aiagent.model.ModelPort;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Cross-session learning. Memory is persisted to a file, so a lesson learned in one run is recalled
 * in a <b>later, separate run</b>. Run it twice: the first run has to learn the hidden rule (the
 * tagline must name the app "Mitra"); the second run already knows it.
 *
 * <p>Works even offline (the stub echoes its input, so the recalled lesson carries the rule through).
 */
public final class MemoryAcrossSessions {

    public static void main(String[] args) {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "java-ai-agent-memory.tsv");
        EpisodicStore memory = new FileEpisodicStore(file);
        ModelPort model = Examples.modelFromEnv();

        String task = "Write a one-line tagline for our app.";
        int prior = memory.recall(task, 5).size();

        System.out.println("== MemoryAcrossSessions ==  model: " + model.name());
        System.out.println("memory file: " + file);
        System.out.println("lessons remembered from EARLIER sessions: " + prior + "\n");

        Reflector reviewer = (t, answer) -> answer.contains("Mitra")
                ? Reflection.ok()
                : Reflection.issue("Always include our app name, 'Mitra', in the tagline.");
        Supplier<Agent> worker = () -> DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a copywriter. Reply with only the tagline.")
                .build();
        Agent agent = ReflectiveAgent.builder()
                .worker(worker).reflector(reviewer).memory(memory).maxAttempts(3)
                .build();

        System.out.println("> " + task);
        AgentResponse r = agent.run(new AgentRequest(task));
        System.out.println("answer: " + r.output() + "\n");

        if (prior == 0) {
            System.out.println("First run: it had to LEARN the rule (see the INFO log). Run it again —");
            System.out.println("the next session starts already knowing it, because the lesson was saved to disk.");
        } else {
            System.out.println("This session started ALREADY knowing the rule from a previous run —");
            System.out.println("that's cross-session learning. (Delete the file above to reset.)");
        }
    }
}
