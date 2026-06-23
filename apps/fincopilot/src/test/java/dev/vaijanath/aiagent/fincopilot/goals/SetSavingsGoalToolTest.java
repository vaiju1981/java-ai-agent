package dev.vaijanath.aiagent.fincopilot.goals;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Argument validation returns an error before any database access (the store source always throws). */
class SetSavingsGoalToolTest {

    private final SetSavingsGoalTool tool = new SetSavingsGoalTool(new SavingsGoalStore(() -> {
        throw new SQLException("the store must not be touched for invalid arguments");
    }));

    private ToolResult invoke(String argsJson) {
        ToolCall call = new ToolCall("c1", "set_savings_goal", argsJson);
        ToolCallContext context = new ToolCallContext(tool.spec(), argsJson, "u1", "u1", "t", "s", null, null);
        return tool.invoke(new ToolInvocation(call, context));
    }

    @Test
    void rejectsAMissingName() {
        assertTrue(invoke("{\"targetAmount\":100}").error());
    }

    @Test
    void rejectsANonPositiveAmount() {
        assertTrue(invoke("{\"name\":\"car\",\"targetAmount\":0}").error());
    }

    @Test
    void rejectsANonNumericAmount() {
        assertTrue(invoke("{\"name\":\"car\",\"targetAmount\":\"lots\"}").error());
    }

    @Test
    void rejectsAMalformedDate() {
        assertTrue(invoke("{\"name\":\"car\",\"targetAmount\":100,\"targetDate\":\"soon\"}").error());
    }
}
