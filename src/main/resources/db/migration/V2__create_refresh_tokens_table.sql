-- V2: refresh_tokens
-- Stores hashed refresh tokens for stateful session management.
-- Stateful = we can revoke individual sessions or all sessions for a user.
-- token_hash: SHA-256 hex of the raw token (64 chars). Never store raw tokens.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)              NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ              NOT NULL,
    created_at  TIMESTAMPTZ              NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id  ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires  ON refresh_tokens(expires_at);
