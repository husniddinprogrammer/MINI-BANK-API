-- ─────────────────────────────────────────────────────────────────────────────
-- V4: Create refresh_tokens table
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    token       VARCHAR(64)  NOT NULL,      -- SHA-256 hex hash of the raw token (64 chars)
    user_id     UUID         NOT NULL,
    expiry_date TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    device_info VARCHAR(200),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT pk_refresh_tokens        PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token  UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

COMMENT ON TABLE  refresh_tokens IS 'Persisted JWT refresh tokens for token rotation. Only SHA-256 hashes are stored.';
COMMENT ON COLUMN refresh_tokens.token IS 'SHA-256 hex hash of the raw refresh token. The raw value is returned to the client and never stored.';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Set to TRUE on logout or token rotation. Revoked tokens cannot be used to obtain new access tokens.';
