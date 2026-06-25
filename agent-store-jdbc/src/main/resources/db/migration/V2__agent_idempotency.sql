-- Turn-level idempotency: a request carrying an idempotency key records its (non-retryable) result
-- here, so a retry returns the prior result across restarts and instances instead of re-running the
-- turn. Keyed by (tenant, idempotency_key); the application scopes the key by principal + session.
-- Mirrors the schema JdbcIdempotencyStore expects; portable across PostgreSQL and SQLite.
CREATE TABLE IF NOT EXISTS agent_idempotency (
    tenant VARCHAR(256) NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL,
    output TEXT NOT NULL,
    blocked INTEGER NOT NULL, -- 0/1, portable across PostgreSQL and SQLite
    stop_reason VARCHAR(256) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (tenant, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_idempotency_created ON agent_idempotency(created_at);
