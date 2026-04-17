-- ─────────────────────────────────────────────────────────────────────────────
-- V7: Seed default admin user
--
-- Password: Admin@123456
-- BCrypt hash (strength 12) generated offline — NEVER store plaintext passwords.
-- Change this password immediately after first deployment.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO users (
    id,
    first_name,
    last_name,
    email,
    password,
    phone_number,
    role,
    enabled,
    account_non_locked,
    failed_login_attempts,
    created_at,
    updated_at,
    created_by
) VALUES (
    gen_random_uuid(),
    'System',
    'Administrator',
    'admin@banking.com',
    -- BCrypt hash of 'Admin@123456' with strength 12
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj4J/8KZSGQm',
    '+998901234567',
    'ROLE_ADMIN',
    TRUE,
    TRUE,
    0,
    now(),
    now(),
    'flyway-migration'
)
ON CONFLICT (email) DO NOTHING;  -- Idempotent: safe to re-run

COMMENT ON TABLE users IS 'Default admin seeded in V7. Change credentials immediately post-deployment.';
