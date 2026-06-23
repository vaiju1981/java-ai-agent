package dev.vaijanath.aiagent.fincopilot.advisor;

import java.util.List;

/**
 * A small curated knowledge base of general personal- and small-business-finance guidance. Each article
 * is general information (not individualized advice); the Advisor retrieves and cites these. Kept in
 * code so the walking product needs no external content store; a larger corpus would move to pgvector.
 */
public final class FinanceKnowledgeBase {

    private FinanceKnowledgeBase() {}

    public record Article(String id, String title, String text) {}

    public static List<Article> articles() {
        return List.of(
                new Article(
                        "emergency-fund",
                        "Emergency fund",
                        "An emergency fund is cash set aside for unexpected expenses such as job loss, medical bills, "
                                + "or urgent repairs. A common guideline is three to six months of essential expenses, "
                                + "held in an accessible, low-risk account separate from day-to-day spending."),
                new Article(
                        "budgeting-50-30-20",
                        "The 50/30/20 budgeting guideline",
                        "The 50/30/20 guideline allocates after-tax income roughly as 50% to needs (housing, food, "
                                + "utilities, minimum debt payments), 30% to wants, and 20% to savings and extra debt "
                                + "repayment. It is a starting point to adjust to your situation, not a strict rule."),
                new Article(
                        "debt-payoff",
                        "Debt payoff: avalanche vs snowball",
                        "Two common debt-payoff methods are the avalanche (pay extra toward the highest-interest debt "
                                + "first, which minimizes total interest) and the snowball (pay the smallest balance "
                                + "first, which can be more motivating). Both keep minimum payments on all debts."),
                new Article(
                        "high-interest-debt",
                        "High-interest debt",
                        "High-interest debt such as credit-card balances often costs more than typical investment "
                                + "returns, so reducing it is frequently prioritized over additional discretionary "
                                + "spending or lower-return saving."),
                new Article(
                        "savings-rate",
                        "Savings rate",
                        "Your savings rate is the share of income you save. A higher savings rate shortens the time "
                                + "needed to reach goals and build resilience; even small, consistent increases compound "
                                + "meaningfully over time."),
                new Article(
                        "subscriptions-audit",
                        "Auditing recurring subscriptions",
                        "Recurring subscriptions can quietly erode a budget. Periodically reviewing recurring charges "
                                + "and cancelling unused ones is a low-effort way to free up monthly cashflow."),
                new Article(
                        "cashflow-basics",
                        "Cashflow basics",
                        "Cashflow is income minus expenses over a period; positive cashflow means you are spending less "
                                + "than you earn. Tracking categories and recurring costs makes it clearer where money "
                                + "goes and where it can be redirected toward goals."),
                new Article(
                        "business-cashflow",
                        "Small-business cashflow",
                        "For small businesses, cashflow management means timing receivables and payables so cash is "
                                + "available when needed, separating business and personal accounts, and keeping a buffer "
                                + "for slower months and tax obligations."));
    }
}
