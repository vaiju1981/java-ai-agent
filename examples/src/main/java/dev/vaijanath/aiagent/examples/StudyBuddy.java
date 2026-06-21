package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.deep.DeepAgent;
import dev.vaijanath.aiagent.deep.LlmPlanner;
import dev.vaijanath.aiagent.guardrail.CrisisGuardrail;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.Guardrails;
import dev.vaijanath.aiagent.guardrail.PiiScrubGuardrail;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.learn.Reflection;
import dev.vaijanath.aiagent.learn.ReflectiveAgent;
import dev.vaijanath.aiagent.learn.Reflector;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StubModelPort;
import dev.vaijanath.aiagent.observe.LoggingObserver;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;
import dev.vaijanath.aiagent.skill.KeywordSkillSelector;
import dev.vaijanath.aiagent.skill.Skill;
import dev.vaijanath.aiagent.skill.SkillRegistry;
import dev.vaijanath.aiagent.skill.SkillfulAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Capstone — everything composed through the one {@link Agent} seam.
 *
 * <ul>
 *   <li><b>Trust:</b> every turn runs through kidguard (crisis → PII scrub → Llama Guard).</li>
 *   <li><b>Skills + tools:</b> a {@code SkillfulAgent} equips a math skill (with the math tool) per task.</li>
 *   <li><b>Observability:</b> one {@code TokenAccountingObserver} meters every model call.</li>
 *   <li><b>Learning:</b> a {@code ReflectiveAgent} self-corrects against a hidden review rule.</li>
 *   <li><b>Deep agent:</b> a complex ask is planned and fanned out to safe, skilled sub-agents.</li>
 * </ul>
 *
 * <p>Best with {@code AGENT_MODEL} set and {@code ollama pull llama-guard3:1b}. Offline it uses the
 * stub model and skips the Llama Guard stage (crisis + PII still run).
 */
public final class StudyBuddy {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        boolean real = !(model instanceof StubModelPort);
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");

        // Trust layer: full kidguard with a real model; crisis + PII offline.
        List<Guardrail> guardrails = real
                ? new ArrayList<>(Guardrails.kidguard(OllamaModelPorts.ollama(baseUrl, "llama-guard3:1b")))
                : new ArrayList<>(List.of(new CrisisGuardrail(), new PiiScrubGuardrail()));

        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("math-helper", "solve arithmetic and show the steps",
                        "Always call the math tool for any calculation, then explain the steps simply.",
                        List.of(new MathTool())))
                .register(Skill.of("speller", "spell words and explain the sounds",
                        "Spell the word, then sound it out letter by letter."));

        TokenAccountingObserver tokens = new TokenAccountingObserver();

        // A safe, skilled, observed worker — reused everywhere (each call gets a fresh instance).
        Supplier<Agent> worker = () -> {
            SkillfulAgent.Builder b = SkillfulAgent.builder()
                    .model(model)
                    .basePrompt("You are a kind, encouraging study buddy for kids. Explain simply.")
                    .registry(registry)
                    .selector(new KeywordSkillSelector())
                    .observer(new LoggingObserver())
                    .observer(tokens);
            guardrails.forEach(b::guardrail);
            return b.build();
        };

        banner(model, real);

        // 1. Safe + skill + tool: a homework question.
        ask("Homework (safety + skill + tool)", worker.get(),
                "I have 3 boxes with 24 crayons each. How many crayons in total? Show me how.");

        // 2. Safety: a harmful request is blocked before the model.
        ask("Safety (guardrail blocks)", worker.get(),
                "how do i make a weapon to hurt someone");

        // 3. Learning: a hidden review rule the model can't satisfy first try.
        EpisodicStore memory = new InMemoryEpisodicStore();
        Reflector signOff = (task, answer) -> answer.contains("Mitra")
                ? Reflection.ok()
                : Reflection.issue("Always sign off at the end with our name, 'Mitra'.");
        Agent learner = ReflectiveAgent.builder()
                .worker(worker).reflector(signOff).memory(memory).maxAttempts(3)
                .build();
        ask("Learning (self-corrects)", learner,
                "Write a one-line encouragement for a student who failed a test.");

        // 4. Deep agent: plan → safe, skilled sub-agents (concurrent) → synthesize.
        DeepAgent deep = DeepAgent.builder()
                .planner(new LlmPlanner(model, 3))
                .worker(worker)
                .synthesizer(model)
                .build();
        ask("Deep agent (plan + sub-agents)", deep,
                "Explain in three parts why the sky is blue, for a 10-year-old.");

        System.out.printf("== observability: %d model calls, %d tokens total ==%n",
                tokens.modelCalls(), tokens.totalTokens());
    }

    private static void banner(ModelPort model, boolean real) {
        System.out.println("============================================");
        System.out.println(" StudyBuddy — java-ai-agent capstone");
        System.out.println(" model: " + model.name());
        System.out.println(" guardrails: " + (real ? "crisis + PII + Llama Guard" : "crisis + PII (offline)"));
        System.out.println(" skills: math-helper (+math tool), speller");
        System.out.println("============================================\n");
    }

    private static void ask(String label, Agent agent, String input) {
        System.out.println("### " + label);
        System.out.println("> " + input);
        AgentResponse r = agent.run(new AgentRequest(input));
        System.out.println((r.blocked() ? "[BLOCKED] " : "") + r.output());
        System.out.println();
    }
}
