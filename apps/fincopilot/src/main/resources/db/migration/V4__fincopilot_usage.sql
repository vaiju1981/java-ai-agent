-- Per-user daily request metering (usage display + quota enforcement).
CREATE TABLE IF NOT EXISTS fincopilot_usage (
    user_id   VARCHAR(64) NOT NULL REFERENCES fincopilot_users(id),
    usage_day DATE        NOT NULL,
    requests  INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_day)
);
