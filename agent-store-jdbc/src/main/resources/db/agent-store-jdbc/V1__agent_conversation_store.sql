CREATE TABLE IF NOT EXISTS agent_turns (
    tenant VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    turn_id VARCHAR(255) NOT NULL,
    first_seq BIGINT NOT NULL,
    message_count INTEGER NOT NULL,
    completed_at BIGINT NOT NULL,
    PRIMARY KEY (tenant, session_id, turn_id),
    UNIQUE (tenant, session_id, first_seq)
);

CREATE TABLE IF NOT EXISTS agent_messages (
    tenant VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    seq BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    tool_call_id VARCHAR(255),
    tool_name VARCHAR(255),
    tool_calls TEXT,
    turn_id VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (tenant, session_id, seq),
    FOREIGN KEY (tenant, session_id, turn_id)
        REFERENCES agent_turns(tenant, session_id, turn_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_messages_created ON agent_messages(created_at);
CREATE INDEX IF NOT EXISTS idx_agent_messages_turn ON agent_messages(tenant, session_id, turn_id);
