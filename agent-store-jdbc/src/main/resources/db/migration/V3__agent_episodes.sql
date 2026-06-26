-- Durable, semantic episodic memory for self-correcting agents (ReflectiveAgent): each Episode is a row
-- here with its task+lesson embedded, so lessons from past mistakes are recalled by similarity and
-- survive restarts / are shared across instances. Mirrors JdbcEpisodicStore.ensureSchema(); portable
-- across PostgreSQL and SQLite.
CREATE TABLE IF NOT EXISTS agent_episodes (
    id VARCHAR(64) NOT NULL,
    tenant VARCHAR(256) NOT NULL,
    task TEXT NOT NULL,
    outcome TEXT NOT NULL,
    success INTEGER NOT NULL, -- 0/1, portable across PostgreSQL and SQLite
    lesson TEXT NOT NULL,
    embedding TEXT NOT NULL, -- JSON float array, ranked by cosine in-application
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_agent_episodes_tenant ON agent_episodes(tenant);
