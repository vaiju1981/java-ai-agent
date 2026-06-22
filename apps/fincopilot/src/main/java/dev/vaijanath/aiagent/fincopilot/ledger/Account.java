package dev.vaijanath.aiagent.fincopilot.ledger;

import java.time.Instant;

/** A financial account owned by a user (e.g. checking, savings, credit, cash). */
public record Account(String id, String userId, String name, String type, String currency, Instant createdAt) {}
