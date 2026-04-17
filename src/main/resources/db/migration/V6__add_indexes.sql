-- ─────────────────────────────────────────────────────────────────────────────
-- V6: Performance indexes for frequent query patterns
-- ─────────────────────────────────────────────────────────────────────────────

-- ── users ────────────────────────────────────────────────────────────────────
-- Email lookup (login, uniqueness check)
CREATE INDEX IF NOT EXISTS idx_users_email         ON users (email);
-- Phone lookup (uniqueness check on registration)
CREATE INDEX IF NOT EXISTS idx_users_phone_number  ON users (phone_number);

-- ── accounts ─────────────────────────────────────────────────────────────────
-- Account number lookup (transfers, external references)
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts (account_number);
-- All accounts by owner (list endpoint)
CREATE INDEX IF NOT EXISTS idx_accounts_owner_id       ON accounts (owner_id);
-- Primary account lookup per user
CREATE INDEX IF NOT EXISTS idx_accounts_owner_primary  ON accounts (owner_id, is_primary) WHERE is_primary = TRUE;

-- ── transactions ─────────────────────────────────────────────────────────────
-- Reference number lookup (idempotency, customer support)
CREATE INDEX IF NOT EXISTS idx_transactions_reference_number ON transactions (reference_number);
-- History queries by source account (most common read pattern)
CREATE INDEX IF NOT EXISTS idx_transactions_source_account   ON transactions (source_account_id, created_at DESC);
-- History queries by target account
CREATE INDEX IF NOT EXISTS idx_transactions_target_account   ON transactions (target_account_id, created_at DESC);
-- Daily/monthly limit aggregation (sum by account + date range)
CREATE INDEX IF NOT EXISTS idx_transactions_source_date      ON transactions (source_account_id, created_at)
    WHERE status = 'COMPLETED';

-- ── refresh_tokens ───────────────────────────────────────────────────────────
-- Token hash lookup (every refresh request)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token   ON refresh_tokens (token);
-- All tokens by user (logout: revoke all)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ── audit_logs ───────────────────────────────────────────────────────────────
-- Admin query: user's audit history
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id_timestamp ON audit_logs (user_id, timestamp DESC);
-- Filter by action type (e.g. all LOGIN failures)
CREATE INDEX IF NOT EXISTS idx_audit_logs_action            ON audit_logs (action);
