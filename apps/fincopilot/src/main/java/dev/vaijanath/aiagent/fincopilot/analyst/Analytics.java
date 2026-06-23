package dev.vaijanath.aiagent.fincopilot.analyst;

import dev.vaijanath.aiagent.fincopilot.ledger.Transaction;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only aggregations over a single user's transactions — the analytical core the Analyst tools and
 * (later) the dashboard endpoints share. Sign convention: positive = income, negative = expense; all
 * reported figures are non-negative magnitudes.
 */
public final class Analytics {

    private final TransactionStore transactions;

    public Analytics(TransactionStore transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public record CategorySpend(String category, BigDecimal spent) {}

    public record MonthFlow(String month, BigDecimal income, BigDecimal expense) {}

    public record Summary(
            int transactionCount,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal net,
            LocalDate firstDate,
            LocalDate lastDate) {}

    /** Expense totals by category, largest first, over the optional date range. */
    public List<CategorySpend> spendingByCategory(String userId, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Transaction t : transactions.listByUser(userId, from, to)) {
            if (t.amount().signum() < 0) {
                byCategory.merge(t.category(), t.amount().negate(), BigDecimal::add);
            }
        }
        return byCategory.entrySet().stream()
                .map(e -> new CategorySpend(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategorySpend::spent).reversed())
                .toList();
    }

    /** Income and expense per calendar month ({@code yyyy-MM}), oldest first. */
    public List<MonthFlow> monthlyCashflow(String userId) {
        Map<String, BigDecimal[]> byMonth = new TreeMap<>();
        for (Transaction t : transactions.listByUser(userId, null, null)) {
            String month = t.date().toString().substring(0, 7);
            BigDecimal[] flow = byMonth.computeIfAbsent(month, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            if (t.amount().signum() >= 0) {
                flow[0] = flow[0].add(t.amount());
            } else {
                flow[1] = flow[1].add(t.amount().negate());
            }
        }
        return byMonth.entrySet().stream()
                .map(e -> new MonthFlow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    /** Overall totals and the covered date range. */
    public Summary summary(String userId) {
        List<Transaction> all = transactions.listByUser(userId, null, null);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        LocalDate first = null;
        LocalDate last = null;
        for (Transaction t : all) {
            if (t.amount().signum() >= 0) {
                income = income.add(t.amount());
            } else {
                expense = expense.add(t.amount().negate());
            }
            if (first == null || t.date().isBefore(first)) {
                first = t.date();
            }
            if (last == null || t.date().isAfter(last)) {
                last = t.date();
            }
        }
        return new Summary(all.size(), income, expense, income.subtract(expense), first, last);
    }
}
