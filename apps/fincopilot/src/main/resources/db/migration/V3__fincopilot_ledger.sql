-- FinCopilot ledger: accounts and transactions, scoped per user.
-- Amount convention: positive = money in (income), negative = money out (expense).

CREATE TABLE IF NOT EXISTS fincopilot_accounts (
    id         VARCHAR(64)  PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL REFERENCES fincopilot_users(id),
    name       VARCHAR(128) NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    currency   VARCHAR(8)   NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS fincopilot_accounts_user_idx ON fincopilot_accounts (user_id);

CREATE TABLE IF NOT EXISTS fincopilot_transactions (
    id          VARCHAR(64)   PRIMARY KEY,
    user_id     VARCHAR(64)   NOT NULL REFERENCES fincopilot_users(id),
    account_id  VARCHAR(64)   NOT NULL REFERENCES fincopilot_accounts(id),
    txn_date    DATE          NOT NULL,
    amount      NUMERIC(18,2) NOT NULL,
    merchant    VARCHAR(256)  NOT NULL DEFAULT '',
    category    VARCHAR(64)   NOT NULL DEFAULT 'uncategorized',
    description VARCHAR(512)  NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS fincopilot_transactions_user_date_idx
    ON fincopilot_transactions (user_id, txn_date);
