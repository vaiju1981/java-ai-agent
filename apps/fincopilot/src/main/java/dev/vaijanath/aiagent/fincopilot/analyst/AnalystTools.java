package dev.vaijanath.aiagent.fincopilot.analyst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.fincopilot.analyst.Analytics.CategorySpend;
import dev.vaijanath.aiagent.fincopilot.analyst.Analytics.MonthFlow;
import dev.vaijanath.aiagent.fincopilot.analyst.Analytics.Summary;
import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.StructuredTool;
import dev.vaijanath.aiagent.tool.StructuredToolResult;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * The Analyst's READ_ONLY tools over a user's transactions. Each is a {@link ContextualTool}: it reads
 * the user id from the governed invocation context (never from the model's arguments), so it can only
 * ever see the authenticated user's data.
 */
public final class AnalystTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NO_ARGS_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";
    private static final String DATE_RANGE_SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"from\":{\"type\":\"string\",\"description\":\"ISO start date yyyy-MM-dd (optional)\"},"
            + "\"to\":{\"type\":\"string\",\"description\":\"ISO end date yyyy-MM-dd (optional)\"}}}";

    private AnalystTools() {}

    /** A user's overall income/expense/net and the date range covered. */
    public static final class FinanceSummaryTool implements ContextualTool {

        private final Analytics analytics;

        public FinanceSummaryTool(Analytics analytics) {
            this.analytics = analytics;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "finance_summary",
                    "Overall totals for the user: transaction count, total income, total expense, net, and date range.",
                    NO_ARGS_SCHEMA,
                    ToolEffect.READ_ONLY);
        }

        @Override
        public ToolResult invoke(ToolInvocation invocation) {
            Summary s = analytics.summary(invocation.context().principal());
            if (s.transactionCount() == 0) {
                return ToolResult.ok("No transactions recorded yet.");
            }
            return ToolResult.ok(String.format(
                    "Transactions: %d (%s to %s). Income: %s. Expense: %s. Net: %s.",
                    s.transactionCount(),
                    s.firstDate(),
                    s.lastDate(),
                    s.totalIncome().toPlainString(),
                    s.totalExpense().toPlainString(),
                    s.net().toPlainString()));
        }
    }

    /** Expense totals by category over an optional date range; also emits a structured payload for the UI. */
    public static final class SpendingByCategoryTool implements StructuredTool {

        private final Analytics analytics;

        public SpendingByCategoryTool(Analytics analytics) {
            this.analytics = analytics;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "spending_by_category",
                    "Expense totals grouped by category, largest first, over an optional date range.",
                    DATE_RANGE_SCHEMA,
                    ToolEffect.READ_ONLY);
        }

        @Override
        public StructuredToolResult invokeStructured(ToolInvocation invocation) {
            JsonNode args = parse(invocation.argumentsJson());
            List<CategorySpend> spend =
                    analytics.spendingByCategory(invocation.context().principal(), date(args, "from"), date(args, "to"));
            if (spend.isEmpty()) {
                return StructuredToolResult.of(ToolResult.ok("No expenses found for that period."));
            }
            StringBuilder sb = new StringBuilder("Spending by category:");
            for (CategorySpend c : spend) {
                sb.append("\n- ").append(c.category()).append(": ").append(c.spent().toPlainString());
            }
            String dataJson = json(Map.of(
                    "type",
                    "spending_by_category",
                    "items",
                    spend.stream().map(c -> new Bar(c.category(), c.spent())).toList()));
            return new StructuredToolResult(ToolResult.ok(sb.toString()), dataJson);
        }
    }

    /** Income vs. expense per calendar month; also emits a structured payload for the UI. */
    public static final class MonthlyCashflowTool implements StructuredTool {

        private final Analytics analytics;

        public MonthlyCashflowTool(Analytics analytics) {
            this.analytics = analytics;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "monthly_cashflow",
                    "Income and expense totals for each calendar month, oldest first.",
                    NO_ARGS_SCHEMA,
                    ToolEffect.READ_ONLY);
        }

        @Override
        public StructuredToolResult invokeStructured(ToolInvocation invocation) {
            List<MonthFlow> flow = analytics.monthlyCashflow(invocation.context().principal());
            if (flow.isEmpty()) {
                return StructuredToolResult.of(ToolResult.ok("No transactions recorded yet."));
            }
            StringBuilder sb = new StringBuilder("Monthly cashflow (income / expense):");
            for (MonthFlow m : flow) {
                sb.append("\n- ")
                        .append(m.month())
                        .append(": ")
                        .append(m.income().toPlainString())
                        .append(" / ")
                        .append(m.expense().toPlainString());
            }
            String dataJson = json(Map.of(
                    "type",
                    "monthly_cashflow",
                    "items",
                    flow.stream().map(m -> new MonthBar(m.month().toString(), m.income(), m.expense())).toList()));
            return new StructuredToolResult(ToolResult.ok(sb.toString()), dataJson);
        }
    }

    /** A category/amount pair for the structured spending payload. */
    private record Bar(String label, BigDecimal value) {}

    /** A month's income/expense for the structured cashflow payload. */
    private record MonthBar(String month, BigDecimal income, BigDecimal expense) {}

    /** Serialize a structured payload to JSON, or null if it can't be serialized (no payload then). */
    private static String json(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static JsonNode parse(String argumentsJson) {
        try {
            return MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (JsonProcessingException e) {
            return MAPPER.createObjectNode();
        }
    }

    private static LocalDate date(JsonNode args, String field) {
        JsonNode node = args.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(node.asText().strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
