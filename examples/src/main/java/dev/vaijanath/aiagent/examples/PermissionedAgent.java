package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;

/**
 * Capability-based tool authorization. Both tools are registered, but the policy is
 * {@code denyEffectful()}: the read-only {@code math} tool runs, while the effectful
 * {@code delete_data} tool is denied by default — no allow-list needed. Swap in
 * {@code ToolApprovers.denyEffectful("delete_data")} to permit it, or a {@code ConsoleToolApprover}
 * to require human approval.
 *
 * <p>Deterministic and offline: a scripted model drives the tool calls so the policy is unmistakable.
 */
public final class PermissionedAgent {

    public static void main(String[] args) {
        // A scripted model: try delete_data (denied) → then add 2+2 (allowed) → then summarize.
        ModelPort scripted = new ModelPort() {
            private int step = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                step++;
                return switch (step) {
                    case 1 -> new ModelResponse("", List.of(new ToolCall("c1", "delete_data", "{}")));
                    case 2 -> new ModelResponse("", List.of(
                            new ToolCall("c2", "math", "{\"op\":\"add\",\"a\":2,\"b\":2}")));
                    default -> ModelResponse.text(summarize(request));
                };
            }

            private String summarize(ModelRequest request) {
                List<Message> tools = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL).toList();
                return "Tool results seen: " + tools.stream().map(Message::content).toList();
            }
        };

        System.out.println("== PermissionedAgent ==  (policy: deny effectful tools by default)\n");

        String out = DefaultAgent.builder()
                .model(scripted)
                .tool(new MathTool())          // READ_ONLY
                .tool(new DeleteDataTool())    // EFFECTFUL
                .toolApprover(ToolApprovers.denyEffectful()) // read-only runs; effectful denied
                .maxSteps(5)
                .build()
                .run(new AgentRequest("Clean up the database, then tell me 2+2."))
                .output();

        System.out.println(out);
        System.out.println("\n→ 'delete_data' is effectful, so it was denied by default; "
                + "'math' is read-only, so it ran and returned 4.");
    }

    /** A deliberately dangerous tool, marked EFFECTFUL — denied unless explicitly authorized. */
    private static final class DeleteDataTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "delete_data",
                    "Permanently delete all records.",
                    "{\"type\":\"object\",\"properties\":{}}",
                    ToolEffect.EFFECTFUL);
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            return ToolResult.ok("ALL DATA DELETED"); // never reached under denyEffectful()
        }
    }
}
