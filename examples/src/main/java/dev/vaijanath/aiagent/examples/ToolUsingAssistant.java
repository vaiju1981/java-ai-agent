package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.observe.LoggingObserver;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;

/**
 * A real multi-tool assistant: the model must chain two tools to answer one question — convert
 * miles→km, then add the distances. Run with {@code AGENT_MODEL} set to see real tool orchestration
 * (the DEBUG trace shows each tool call).
 */
public final class ToolUsingAssistant {

    public static void main(String[] args) {
        ModelPort model = Examples.modelFromEnv();
        TokenAccountingObserver tokens = new TokenAccountingObserver();

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a precise assistant. Use the 'math' and 'convert' tools for "
                        + "ALL calculations and conversions — never compute in your head. "
                        + "When you have the final number, state it in one sentence.")
                .tool(new MathTool())
                .tool(new UnitConverterTool())
                .observer(new LoggingObserver())
                .observer(tokens)
                .maxSteps(10)
                .build();

        String task = "I jogged 5 miles this morning and 8 kilometers this evening. Convert the "
                + "miles to kilometers, add both distances together, and tell me the total in km.";

        System.out.println("== ToolUsingAssistant ==  model: " + model.name() + "\n");
        System.out.println("> " + task + "\n");
        System.out.println(agent.run(new AgentRequest(task)).output());
        System.out.printf("%n[%d model calls, %d tokens]%n", tokens.modelCalls(), tokens.totalTokens());
    }
}
