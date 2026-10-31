-- Legacy rows have no reliable provider metadata; default to password authentication.
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'PASSWORD';
ALTER TABLE users ADD CONSTRAINT users_auth_provider_check
    CHECK (auth_provider IN ('PASSWORD', 'GOOGLE'));

-- Enforce email identity across providers, including concurrent registrations.
-- Existing case-only duplicates must be resolved before this migration can succeed.
CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email));
