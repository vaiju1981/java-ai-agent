-- FinCopilot savings goals, scoped per user. Created via the chat agent's effectful set_savings_goal
-- tool, which requires human-in-the-loop approval before it writes.
CREATE TABLE IF NOT EXISTS fincopilot_goals (
    id            VARCHAR(64)   PRIMARY KEY,
    user_id       VARCHAR(64)   NOT NULL REFERENCES fincopilot_users(id),
    name          VARCHAR(128)  NOT NULL,
    target_amount NUMERIC(18,2) NOT NULL,
    target_date   DATE,
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS fincopilot_goals_user_idx ON fincopilot_goals (user_id);
