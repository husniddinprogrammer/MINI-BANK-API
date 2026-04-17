-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Create transactions table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE transactions (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    reference_number       VARCHAR(30)    NOT NULL,
    source_account_id      UUID,                          -- NULL for DEPOSIT
    target_account_id      UUID,                          -- NULL for WITHDRAWAL
    type                   VARCHAR(20)    NOT NULL,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    amount                 NUMERIC(19,4)  NOT NULL,
    fee                    NUMERIC(19,4)  NOT NULL DEFAULT 0.0000,
    balance_before_source  NUMERIC(19,4),
    balance_after_source   NUMERIC(19,4),
    balance_before_target  NUMERIC(19,4),
    balance_after_target   NUMERIC(19,4),
    currency               VARCHAR(10)    NOT NULL,
    description            VARCHAR(255),
    failure_reason         VARCHAR(500),
    processed_at           TIMESTAMP,
    created_at             TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP      NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),

    CONSTRAINT pk_transactions                  PRIMARY KEY (id),
    CONSTRAINT uq_transactions_reference        UNIQUE (reference_number),
    CONSTRAINT fk_transactions_source_account   FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transactions_target_account   FOREIGN KEY (target_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_transactions_type   CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    CONSTRAINT chk_transactions_currency CHECK (currency IN ('UZS', 'USD', 'EUR')),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_fee_non_negative CHECK (fee >= 0)
);

COMMENT ON TABLE  transactions IS 'Immutable financial event log. Balance snapshots provide irrefutable audit trail.';
COMMENT ON COLUMN transactions.reference_number IS 'Human-readable reference: TXN-YYYYMMDD-UUID8. Exposed to users for support lookups.';
COMMENT ON COLUMN transactions.balance_before_source IS 'Source account balance before the debit — captured atomically for dispute resolution.';
