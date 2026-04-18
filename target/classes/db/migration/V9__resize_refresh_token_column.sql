-- ─────────────────────────────────────────────────────────────────────────────
-- V9: Resize refresh_tokens.token from VARCHAR(64) to VARCHAR(128)
--
-- JJWT 0.12.x generates HS512 refresh tokens that exceed 64 characters.
-- A VARCHAR(64) column silently truncates or rejects valid tokens,
-- causing spurious "invalid refresh token" errors in production.
-- 128 characters provides headroom for the full Base64-encoded token value.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE refresh_tokens
    ALTER COLUMN token TYPE VARCHAR(128);
