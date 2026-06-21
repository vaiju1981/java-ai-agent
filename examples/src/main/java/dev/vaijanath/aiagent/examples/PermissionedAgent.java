package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import java.util.List;

/**
 * Tool authorization in action. The agent is permitted only the {@code math} tool; when the model
 * tries a sensitive {@code delete_data} tool it is denied and the model reacts to the refusal. Swap
 * {@code ToolApprovers.allowList(...)} for a {@code ConsoleToolApprover} to require human approval.
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
                return "Tool results seen: "
                        + tools.stream().map(Message::content).toList();
            }
        };

        System.out.println("== PermissionedAgent ==  (allow-list: only 'math')\n");

        String out = DefaultAgent.builder()
                .model(scripted)
                .tool(new MathTool())
                .toolApprover(ToolApprovers.allowList("math")) // 'delete_data' is NOT permitted
                .maxSteps(5)
                .build()
                .run(new AgentRequest("Clean up the database, then tell me 2+2."))
                .output();

        System.out.println(out);
        System.out.println("\n→ 'delete_data' was blocked by the policy; 'math' ran and returned 4.");
    }
}
