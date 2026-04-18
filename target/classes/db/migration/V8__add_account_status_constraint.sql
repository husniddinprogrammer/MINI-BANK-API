-- ─────────────────────────────────────────────────────────────────────────────
-- V8: Prevent closed account reactivation at the database level
--
-- SECURITY: Service layer alone is insufficient — direct DB access or a future
-- admin tool could bypass it. This trigger enforces the state machine invariant:
-- CLOSED is a terminal state and cannot transition to ACTIVE or FROZEN.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION check_account_reactivation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'CLOSED' AND NEW.status != 'CLOSED' THEN
        RAISE EXCEPTION
            'Cannot reactivate a closed account: %', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_account_reactivation
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION check_account_reactivation();

COMMENT ON FUNCTION check_account_reactivation() IS
    'Enforces terminal CLOSED state: a closed account cannot be reactivated. Mirrors AccountServiceImpl.closeAccount() guard at the DB level.';
