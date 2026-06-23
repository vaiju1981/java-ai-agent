package dev.vaijanath.aiagent.fincopilot.goals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * An <b>effectful</b> tool that creates a savings goal for the signed-in user. Because it writes, the
 * governed runtime denies it by default and escalates to the human-in-the-loop approver — so the model
 * can propose a goal, but nothing is written until the user approves (see the ApprovalHandler seam).
 */
public final class SetSavingsGoalTool implements ContextualTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"name\":{\"type\":\"string\",\"description\":\"what the goal is for, e.g. 'emergency fund'\"},"
            + "\"targetAmount\":{\"type\":\"number\",\"description\":\"the amount to save\"},"
            + "\"targetDate\":{\"type\":\"string\",\"description\":\"optional target date, YYYY-MM-DD\"}},"
            + "\"required\":[\"name\",\"targetAmount\"]}";

    private final SavingsGoalStore store;

    public SetSavingsGoalTool(SavingsGoalStore store) {
        this.store = store;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "set_savings_goal",
                "Create a savings goal for the user. This changes the user's data and requires their approval.",
                SCHEMA,
                ToolEffect.EFFECTFUL);
    }

    @Override
    public ToolResult invoke(ToolInvocation invocation) {
        JsonNode args;
        try {
            args = MAPPER.readTree(invocation.argumentsJson());
        } catch (JsonProcessingException e) {
            return ToolResult.error("could not parse the goal arguments");
        }
        String name = args.path("name").asText("").strip();
        if (name.isEmpty()) {
            return ToolResult.error("a goal name is required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(args.path("targetAmount").asText(""));
        } catch (NumberFormatException e) {
            return ToolResult.error("targetAmount must be a number");
        }
        if (amount.signum() <= 0) {
            return ToolResult.error("targetAmount must be greater than zero");
        }
        LocalDate targetDate = null;
        String date = args.path("targetDate").asText("").strip();
        if (!date.isEmpty()) {
            try {
                targetDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return ToolResult.error("targetDate must be a date in YYYY-MM-DD form");
            }
        }
        SavingsGoal goal = store.create(invocation.context().principal(), name, amount, targetDate);
        return ToolResult.ok("Created savings goal '" + goal.name() + "': target "
                + goal.targetAmount().toPlainString()
                + (targetDate != null ? " by " + targetDate : "") + ".");
    }
}
