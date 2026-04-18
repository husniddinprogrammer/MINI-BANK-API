-- Enforce at most one primary account per user at the database level.
-- A partial unique index is used so that non-primary accounts (is_primary = false)
-- are not covered by the constraint — only the single true row is unique per owner.
CREATE UNIQUE INDEX idx_accounts_one_primary_per_user
    ON accounts (owner_id)
    WHERE is_primary = true;
