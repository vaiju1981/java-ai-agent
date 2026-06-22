-- FinCopilot consumer auth. (V1 — the conversation store — ships in agent-store-jdbc; app migrations
-- start at V2.)

CREATE TABLE IF NOT EXISTS fincopilot_users (
    id            VARCHAR(64)  PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS fincopilot_sessions (
    token      VARCHAR(128) PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL REFERENCES fincopilot_users(id),
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS fincopilot_sessions_user_idx ON fincopilot_sessions (user_id);
