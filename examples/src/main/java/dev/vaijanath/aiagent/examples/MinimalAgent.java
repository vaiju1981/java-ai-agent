package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;

/**
 * Rung 1 — the smallest possible agent: a model and a prompt. Everything else (tools, guardrails,
 * memory, observers) is optional and added only when needed.
 */
public final class MinimalAgent {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a helpful assistant. Keep answers to one sentence.")
                .build();

        System.out.println(agent.run(new AgentRequest("Say hello and name one benefit of the JVM.")).output());
    }
}
