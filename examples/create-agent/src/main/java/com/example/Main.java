package com.example;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.anthropic.AnthropicModelPort;
import dev.vaijanath.aiagent.model.ModelPort;

/**
 * A minimal java-ai-agent app: a model and a prompt, one turn. Copy this folder and make it yours —
 * add tools, guardrails, memory, or multi-agent orchestration as you grow (see the project Cookbook).
 */
public final class Main {

    public static void main(String[] args) {
        // Reads ANTHROPIC_API_KEY from the environment.
        // For GPT models, depend on agent-openai instead and use OpenAiModelPort.fromEnv() (OPENAI_API_KEY).
        ModelPort model = AnthropicModelPort.fromEnv();

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a helpful assistant. Be concise.")
                .build();

        String question = args.length > 0
                ? String.join(" ", args)
                : "Say hello and name one benefit of the JVM.";

        System.out.println(agent.run(new AgentRequest(question)).output());
    }
}
