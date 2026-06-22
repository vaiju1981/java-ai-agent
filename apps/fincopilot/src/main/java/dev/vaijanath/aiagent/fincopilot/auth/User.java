package dev.vaijanath.aiagent.fincopilot.auth;

import java.time.Instant;

/** A FinCopilot user account. {@code passwordHash} is a BCrypt hash, never the raw password. */
public record User(String id, String email, String passwordHash, Instant createdAt) {}
