package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

/** Compares a category's spend in a given month against a configured monthly budget. */
public final class BudgetTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String jdbcUrl;
    private final Map<String, Double> monthlyBudgets;

    public BudgetTool(String jdbcUrl, Map<String, Double> monthlyBudgets) {
        this.jdbcUrl = jdbcUrl;
        this.monthlyBudgets = Map.copyOf(monthlyBudgets);
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "budget_check",
                "Compare a category's spending in a month (1-12) against its monthly budget.",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"category\":{\"type\":\"string\"},\"month\":{\"type\":\"integer\"}},"
                        + "\"required\":[\"category\",\"month\"]}");
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        String category;
        int month;
        try {
            var args = MAPPER.readTree(argumentsJson);
            category = args.path("category").asText("");
            month = args.path("month").asInt(0);
        } catch (Exception e) {
            return ToolResult.error("could not parse arguments: " + argumentsJson);
        }
        Double budget = monthlyBudgets.get(category);
        if (budget == null) {
            return ToolResult.error("no budget configured for category '" + category + "'");
        }
        if (month < 1 || month > 12) {
            return ToolResult.error("month must be 1-12");
        }

        String mm = String.format("%02d", month);
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(SUM(amount),0) FROM transactions "
                                + "WHERE category = ? AND strftime('%m', txn_date) = ?")) {
            ps.setString(1, category);
            ps.setString(2, mm);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                double spent = rs.getDouble(1);
                double diff = budget - spent;
                String verdict = diff >= 0
                        ? String.format("under budget by %.2f", diff)
                        : String.format("OVER budget by %.2f", -diff);
                return ToolResult.ok(String.format(
                        "%s month %d: spent %.2f of %.2f budget (%s)", category, month, spent, budget, verdict));
            }
        } catch (Exception e) {
            return ToolResult.error("budget check failed: " + e.getMessage());
        }
    }
}
