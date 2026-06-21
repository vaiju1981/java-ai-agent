package dev.vaijanath.aiagent.demos.finance;
import static dev.vaijanath.aiagent.demos.SimpleTool.numbers;

import dev.vaijanath.aiagent.demos.SimpleTool;
import dev.vaijanath.aiagent.demos.SqlTool;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A realistic ~24-tool personal-finance toolkit: 3 data tools plus many small calculators. */
final class FinanceTools {

    private FinanceTools() {}

    static List<Tool> toolkit(String jdbcUrl, Map<String, Double> budgets) {
        List<Tool> t = new ArrayList<>();

        // Data tools.
        t.add(new SqlTool(jdbcUrl, 50));
        t.add(new CategorizeMerchantTool());
        t.add(new BudgetTool(jdbcUrl, budgets));

        // Calculators / lookups (pure functions).
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
        t.add(new SimpleTool("currency_convert", "Convert an amount using a given exchange rate.",
                numbers("amount", "rate"), n -> f(n.path("amount").asDouble() * n.path("rate").asDouble())));
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
        t.add(new SimpleTool("percent_change", "Percentage change from one value to another.",
                numbers("from", "to"), n -> {
                    double from = n.path("from").asDouble();
                    return from == 0 ? "n/a" : f(100 * (n.path("to").asDouble() - from) / from) + "%";
                }));
        t.add(new SimpleTool("annual_to_monthly", "Convert an annual amount to monthly.",
                numbers("annual"), n -> f(n.path("annual").asDouble() / 12)));
        t.add(new SimpleTool("monthly_to_annual", "Convert a monthly amount to annual.",
                numbers("monthly"), n -> f(n.path("monthly").asDouble() * 12)));
        t.add(new SimpleTool("hourly_to_annual", "Annual salary from an hourly wage.",
                numbers("hourly", "hours_per_week"), n ->
                        f(n.path("hourly").asDouble() * n.path("hours_per_week").asDouble() * 52)));
        t.add(new SimpleTool("inflation_adjusted", "Value after inflation over some years.",
                numbers("amount", "annual_inflation_pct", "years"), n -> {
                    double a = n.path("amount").asDouble();
                    double r = n.path("annual_inflation_pct").asDouble() / 100;
                    return f(a / Math.pow(1 + r, n.path("years").asDouble()));
                }));
        t.add(new SimpleTool("rule_of_72", "Approximate years to double at an annual rate.",
                numbers("annual_rate_pct"), n -> {
                    double r = n.path("annual_rate_pct").asDouble();
                    return r == 0 ? "never" : f(72 / r) + " years";
                }));
        t.add(new SimpleTool("effective_annual_rate", "Effective annual rate from a nominal rate.",
                numbers("nominal_pct", "periods_per_year"), n -> {
                    double r = n.path("nominal_pct").asDouble() / 100;
                    double m = Math.max(1, n.path("periods_per_year").asDouble());
                    return f(100 * (Math.pow(1 + r / m, m) - 1)) + "%";
                }));
        t.add(new SimpleTool("break_even_units", "Units to break even (fixed / (price - variable)).",
                numbers("fixed_cost", "price", "variable_cost"), n -> {
                    double margin = n.path("price").asDouble() - n.path("variable_cost").asDouble();
                    return margin <= 0 ? "never (non-positive margin)"
                            : (long) Math.ceil(n.path("fixed_cost").asDouble() / margin) + " units";
                }));
        return t;
    }

    private static String f(double d) {
        return String.format("%.2f", d);
    }
}
