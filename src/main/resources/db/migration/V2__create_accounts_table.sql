-- ─────────────────────────────────────────────────────────────────────────────
-- V2: Create accounts table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE accounts (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    account_number        VARCHAR(20)    NOT NULL,
    owner_id              UUID           NOT NULL,
    account_type          VARCHAR(20)    NOT NULL,
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    balance               NUMERIC(19,4)  NOT NULL DEFAULT 0.0000,
    currency              VARCHAR(10)    NOT NULL DEFAULT 'UZS',
    daily_transfer_limit  NUMERIC(19,4),
    monthly_transfer_limit NUMERIC(19,4),
    is_primary            BOOLEAN        NOT NULL DEFAULT FALSE,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP      NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),

    CONSTRAINT pk_accounts                PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_owner         FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT chk_accounts_type   CHECK (account_type IN ('SAVINGS', 'CHECKING')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('UZS', 'USD', 'EUR')),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

COMMENT ON TABLE  accounts IS 'Bank accounts owned by users. Each account has a unique 16-digit account number.';
COMMENT ON COLUMN accounts.version IS 'Optimistic lock version — incremented by Hibernate on every UPDATE to detect concurrent modifications.';
COMMENT ON COLUMN accounts.balance IS 'Monetary precision 19,4 per banking standard. Must never go below zero.';
COMMENT ON COLUMN accounts.is_primary IS 'At most one primary account per user (enforced in service layer).';
