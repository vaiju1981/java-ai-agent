package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link Agent} that equips itself per request: it selects relevant skills, then builds a
 * {@link DefaultAgent} with those skills' instructions appended to the system prompt and their tools
 * registered. Progressive disclosure — only the selected skills' instructions enter the model's
 * context.
 */
public final class SkillfulAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SkillfulAgent.class);

    private final ModelPort model;
    private final String basePrompt;
    private final SkillRegistry registry;
    private final SkillSelector selector;
    private final List<Tool> baseTools;
    private final List<Guardrail> guardrails;
    private final List<AgentObserver> observers;
    private final int maxSteps;

    private SkillfulAgent(Builder b) {
        this.model = Objects.requireNonNull(b.model, "model");
        this.basePrompt = b.basePrompt == null ? "" : b.basePrompt;
        this.registry = Objects.requireNonNull(b.registry, "registry");
        this.selector = Objects.requireNonNull(b.selector, "selector");
        this.baseTools = List.copyOf(b.baseTools);
        this.guardrails = List.copyOf(b.guardrails);
        this.observers = List.copyOf(b.observers);
        this.maxSteps = b.maxSteps;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        List<Skill> selected = selector.select(registry, request.input());
        log.info("equipped {} skill(s): {}", selected.size(), selected.stream().map(Skill::name).toList());

        StringBuilder prompt = new StringBuilder(basePrompt);
        DefaultAgent.Builder builder = DefaultAgent.builder().model(model).maxSteps(maxSteps);
        baseTools.forEach(builder::tool);
        guardrails.forEach(builder::guardrail);
        observers.forEach(builder::observer);

        for (Skill skill : selected) {
            if (!prompt.isEmpty()) {
                prompt.append("\n\n");
            }
            prompt.append("# Skill: ").append(skill.name()).append('\n').append(skill.instructions());
            skill.tools().forEach(builder::tool);
        }

        return builder.systemPrompt(prompt.toString()).build().run(request);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ModelPort model;
        private String basePrompt;
        private SkillRegistry registry;
        private SkillSelector selector;
        private final List<Tool> baseTools = new ArrayList<>();
        private final List<Guardrail> guardrails = new ArrayList<>();
        private final List<AgentObserver> observers = new ArrayList<>();
        private int maxSteps = 8;

        public Builder model(ModelPort model) {
            this.model = model;
            return this;
        }

        public Builder basePrompt(String basePrompt) {
            this.basePrompt = basePrompt;
            return this;
        }

        public Builder registry(SkillRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder selector(SkillSelector selector) {
            this.selector = selector;
            return this;
        }

        public Builder tool(Tool tool) {
            this.baseTools.add(tool);
            return this;
        }

        public Builder guardrail(Guardrail guardrail) {
            this.guardrails.add(guardrail);
            return this;
        }

        public Builder observer(AgentObserver observer) {
            this.observers.add(observer);
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public SkillfulAgent build() {
            return new SkillfulAgent(this);
        }
    }
}
