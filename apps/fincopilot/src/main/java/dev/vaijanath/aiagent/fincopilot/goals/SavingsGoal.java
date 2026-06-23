package dev.vaijanath.aiagent.fincopilot.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A savings goal owned by a user. {@code targetDate} is optional (null = no deadline). */
public record SavingsGoal(
        String id, String userId, String name, BigDecimal targetAmount, LocalDate targetDate, Instant createdAt) {}
