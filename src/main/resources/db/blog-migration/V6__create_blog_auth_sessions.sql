CREATE TABLE blog_auth_sessions (
    token_hash VARCHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_blog_auth_sessions_user ON blog_auth_sessions(user_id);
CREATE INDEX idx_blog_auth_sessions_expiry ON blog_auth_sessions(expires_at);
