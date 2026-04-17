-- ─────────────────────────────────────────────────────────────────────────────
-- V1: Create users table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password              VARCHAR(255) NOT NULL,
    phone_number          VARCHAR(20)  NOT NULL,
    date_of_birth         DATE,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_locked    BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email        UNIQUE (email),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT chk_users_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

COMMENT ON TABLE  users IS 'Core user accounts for the banking system';
COMMENT ON COLUMN users.password IS 'BCrypt-hashed password (strength 12). Never stored in plaintext.';
COMMENT ON COLUMN users.failed_login_attempts IS 'Incremented on each failed login. Reset on success. Triggers lockout at threshold.';
COMMENT ON COLUMN users.locked_until IS 'When set, account is locked until this timestamp (timed lockout after brute-force detection).';
