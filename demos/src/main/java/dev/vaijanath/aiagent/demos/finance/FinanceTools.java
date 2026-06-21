package dev.vaijanath.aiagent.demos.finance;

import static dev.vaijanath.aiagent.demos.SimpleTool.numbers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.demos.SimpleTool;
import dev.vaijanath.aiagent.demos.Sql;
import dev.vaijanath.aiagent.demos.SqlTool;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A personal-finance toolkit built around the user's real transactions. It has three groups:
 * data lookups ({@code sql}, {@code categorize_merchant}, {@code budget_check}), data-aware analyses
 * that aggregate the transactions ({@code spending_by_category}, {@code top_merchants},
 * {@code recurring_subscriptions}, {@code largest_expenses}, {@code monthly_spending},
 * {@code category_budget_status}), and stateless planning calculators. The breadth also validates
 * that the agent picks the right tool when many are available.
 */
final class FinanceTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FinanceTools() {}

    @FunctionalInterface
    private interface SqlFn {
        String apply(Connection c, JsonNode args) throws Exception;
    }

    static List<Tool> toolkit(String jdbcUrl, Map<String, Double> budgets) {
        List<Tool> t = new ArrayList<>();

        // Data lookups.
        t.add(new SqlTool(jdbcUrl, 50));
        t.add(new CategorizeMerchantTool());
        t.add(new BudgetTool(jdbcUrl, budgets));

        // Data-aware analyses over the transactions.
        t.add(dbTool(jdbcUrl, "spending_by_category",
                "Total spend per category. Optionally restrict to one month (1-12).",
                monthSchema(), (c, a) -> {
                    String where = monthClause(a);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT category, ROUND(SUM(amount),2) AS total "
                                    + "FROM transactions" + where + " GROUP BY category ORDER BY total DESC")) {
                        return Sql.table(rs, 50);
                    }
                }));

        t.add(dbTool(jdbcUrl, "top_merchants",
                "The merchants you spend the most at, with total spend and transaction count.",
                limitSchema(), (c, a) -> {
                    int limit = clamp(a.path("limit").asInt(5), 1, 50);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT merchant, ROUND(SUM(amount),2) AS total, "
                                    + "COUNT(*) AS txns FROM transactions GROUP BY merchant "
                                    + "ORDER BY total DESC LIMIT " + limit)) {
                        return Sql.table(rs, limit);
                    }
                }));

        t.add(dbTool(jdbcUrl, "recurring_subscriptions",
                "Find likely subscriptions: a merchant charging the same amount across many months. "
                        + "min_months defaults to 6.",
                "{\"type\":\"object\",\"properties\":{\"min_months\":{\"type\":\"integer\"}}}", (c, a) -> {
                    int minMonths = clamp(a.path("min_months").asInt(6), 2, 12);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT merchant, ROUND(amount,2) AS amount, "
                                    + "COUNT(DISTINCT strftime('%Y-%m', txn_date)) AS months "
                                    + "FROM transactions GROUP BY merchant, ROUND(amount,2) "
                                    + "HAVING months >= " + minMonths + " ORDER BY months DESC, amount DESC")) {
                        return Sql.table(rs, 50);
                    }
                }));

        t.add(dbTool(jdbcUrl, "largest_expenses", "The single largest transactions.",
                limitSchema(), (c, a) -> {
                    int limit = clamp(a.path("limit").asInt(5), 1, 50);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT txn_date, merchant, category, amount "
                                    + "FROM transactions ORDER BY amount DESC LIMIT " + limit)) {
                        return Sql.table(rs, limit);
                    }
                }));

        t.add(dbTool(jdbcUrl, "monthly_spending", "Total spend per month, to see the trend over time.",
                "{\"type\":\"object\",\"properties\":{}}", (c, a) -> {
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT strftime('%Y-%m', txn_date) AS month, "
                                    + "ROUND(SUM(amount),2) AS total FROM transactions "
                                    + "GROUP BY month ORDER BY month")) {
                        return Sql.table(rs, 50);
                    }
                }));

        t.add(dbTool(jdbcUrl, "category_budget_status",
                "Spend vs budget for every category at once. Optionally restrict to one month (1-12).",
                monthSchema(), (c, a) -> {
                    String where = monthClause(a);
                    Map<String, Double> spent = new LinkedHashMap<>();
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT category, ROUND(SUM(amount),2) AS total "
                                    + "FROM transactions" + where + " GROUP BY category")) {
                        while (rs.next()) {
                            spent.put(rs.getString(1), rs.getDouble(2));
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, Double> e : budgets.entrySet()) {
                        double s = spent.getOrDefault(e.getKey(), 0.0);
                        double diff = e.getValue() - s;
                        sb.append(String.format("%s: spent %.2f of %.2f budget (%s)%n", e.getKey(), s, e.getValue(),
                                diff >= 0 ? String.format("under by %.2f", diff)
                                        : String.format("OVER by %.2f", -diff)));
                    }
                    return sb.toString().strip();
                }));

        // Planning calculators (stateless, pure functions).
        t.add(new SimpleTool("tip_calculator", "Calculate a tip and the total for a bill.",
                numbers("amount", "percent"), n -> {
                    double a = n.path("amount").asDouble();
                    double tip = a * n.path("percent").asDouble() / 100;
                    return f(tip) + " tip, total " + f(a + tip);
                }));
        t.add(new SimpleTool("split_bill", "Split a bill evenly among people.",
                numbers("amount", "people"), n ->
                        f(n.path("amount").asDouble() / Math.max(1, n.path("people").asInt())) + " per person"));
        t.add(new SimpleTool("sales_tax", "Add sales tax to an amount.",
                numbers("amount", "rate_pct"), n -> {
                    double a = n.path("amount").asDouble();
                    return "total " + f(a * (1 + n.path("rate_pct").asDouble() / 100));
                }));
        t.add(new SimpleTool("discount_price", "Apply a percentage discount to a price.",
                numbers("price", "discount_pct"), n ->
                        f(n.path("price").asDouble() * (1 - n.path("discount_pct").asDouble() / 100))));
        t.add(new SimpleTool("compound_interest", "Future value with annual compounding.",
                numbers("principal", "annual_rate_pct", "years"), n -> {
                    double p = n.path("principal").asDouble();
                    double r = n.path("annual_rate_pct").asDouble() / 100;
                    return f(p * Math.pow(1 + r, n.path("years").asDouble()));
                }));
        t.add(new SimpleTool("future_savings", "Future value of fixed monthly contributions.",
                numbers("monthly", "annual_rate_pct", "years"), n -> {
                    double c = n.path("monthly").asDouble();
                    double r = n.path("annual_rate_pct").asDouble() / 100 / 12;
                    double m = n.path("years").asDouble() * 12;
                    return f(r == 0 ? c * m : c * (Math.pow(1 + r, m) - 1) / r);
                }));
        t.add(new SimpleTool("loan_monthly_payment", "Monthly payment for a fixed-rate loan.",
                numbers("principal", "annual_rate_pct", "years"), n -> {
                    double p = n.path("principal").asDouble();
                    double r = n.path("annual_rate_pct").asDouble() / 100 / 12;
                    double m = n.path("years").asDouble() * 12;
                    return f(r == 0 ? p / m : p * r / (1 - Math.pow(1 + r, -m)));
                }));
        t.add(new SimpleTool("debt_payoff_months", "Months to pay off a balance at a fixed payment.",
                numbers("balance", "monthly_payment", "annual_rate_pct"), n -> {
                    double b = n.path("balance").asDouble();
                    double pay = n.path("monthly_payment").asDouble();
                    double r = n.path("annual_rate_pct").asDouble() / 100 / 12;
                    if (r == 0) {
                        return Math.ceil(b / pay) + " months";
                    }
                    if (pay <= b * r) {
                        return "payment too low to ever pay off";
                    }
                    return (long) Math.ceil(-Math.log(1 - r * b / pay) / Math.log(1 + r)) + " months";
                }));
        t.add(new SimpleTool("savings_rate", "Savings rate from income and spending (percent).",
                numbers("income", "spending"), n -> {
                    double i = n.path("income").asDouble();
                    return i == 0 ? "0%" : f(100 * (i - n.path("spending").asDouble()) / i) + "%";
                }));
        t.add(new SimpleTool("emergency_fund_target", "Target emergency fund = monthly expenses x months.",
                numbers("monthly_expenses", "months"), n ->
                        f(n.path("monthly_expenses").asDouble() * n.path("months").asDouble())));
        t.add(new SimpleTool("net_after_tax", "Net amount after a tax percentage.",
                numbers("gross", "tax_pct"), n ->
                        f(n.path("gross").asDouble() * (1 - n.path("tax_pct").asDouble() / 100))));
        t.add(new SimpleTool("roi", "Return on investment (percent).",
                numbers("gain", "cost"), n -> {
                    double c = n.path("cost").asDouble();
                    return c == 0 ? "n/a" : f(100 * n.path("gain").asDouble() / c) + "%";
                }));
        t.add(new SimpleTool("hourly_to_annual", "Annual salary from an hourly wage.",
                numbers("hourly", "hours_per_week"), n ->
                        f(n.path("hourly").asDouble() * n.path("hours_per_week").asDouble() * 52)));
        t.add(new SimpleTool("inflation_adjusted", "Today's value of a future amount after inflation.",
                numbers("amount", "annual_inflation_pct", "years"), n -> {
                    double a = n.path("amount").asDouble();
                    double r = n.path("annual_inflation_pct").asDouble() / 100;
                    return f(a / Math.pow(1 + r, n.path("years").asDouble()));
                }));
        t.add(new SimpleTool("effective_annual_rate", "Effective annual rate (APY) from a nominal rate.",
                numbers("nominal_pct", "periods_per_year"), n -> {
                    double r = n.path("nominal_pct").asDouble() / 100;
                    double m = Math.max(1, n.path("periods_per_year").asDouble());
                    return f(100 * (Math.pow(1 + r / m, m) - 1)) + "%";
                }));
        return t;
    }

    private static Tool dbTool(String url, String name, String description, String schema, SqlFn fn) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, description, schema);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try (Connection c = DriverManager.getConnection(url)) {
                    return ToolResult.ok(fn.apply(c, MAPPER.readTree(argumentsJson)));
                } catch (Exception e) {
                    return ToolResult.error(name + " failed: " + e.getMessage());
                }
            }
        };
    }

    /** A {@code WHERE strftime('%m', ...)} clause for a valid month argument, else the empty string. */
    private static String monthClause(JsonNode args) {
        int month = args.path("month").asInt(0);
        return (month >= 1 && month <= 12)
                ? " WHERE strftime('%m', txn_date) = '" + String.format("%02d", month) + "'"
                : "";
    }

    private static String monthSchema() {
        return "{\"type\":\"object\",\"properties\":{\"month\":{\"type\":\"integer\"}}}";
    }

    private static String limitSchema() {
        return "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    private static String f(double d) {
        return String.format("%.2f", d);
    }
}
