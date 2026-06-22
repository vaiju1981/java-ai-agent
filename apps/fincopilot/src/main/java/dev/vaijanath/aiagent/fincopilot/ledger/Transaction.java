package dev.vaijanath.aiagent.fincopilot.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single ledger entry. {@code amount} follows the sign convention: positive = money in (income),
 * negative = money out (expense).
 */
public record Transaction(
        String id,
        String userId,
        String accountId,
        LocalDate date,
        BigDecimal amount,
        String merchant,
        String category,
        String description) {}
