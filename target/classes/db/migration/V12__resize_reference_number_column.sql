-- reference_number was VARCHAR(30), which fits normal TXN-YYYYMMDD-UUID8 (21 chars)
-- but overflows idempotency references: TXN-IDMP-<uuid> = 45 chars.
ALTER TABLE transactions ALTER COLUMN reference_number TYPE VARCHAR(64);
