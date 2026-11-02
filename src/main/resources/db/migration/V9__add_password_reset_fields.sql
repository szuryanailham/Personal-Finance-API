ALTER TABLE users ADD COLUMN password_reset_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN password_reset_expires_at BIGINT;
CREATE UNIQUE INDEX users_password_reset_token_hash_unique ON users (password_reset_token_hash);
