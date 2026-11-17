ALTER TABLE users
    ADD COLUMN photo_profile TEXT,
    ADD COLUMN description TEXT;

CREATE TABLE author_social_links (
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL CHECK (btrim(platform) <> ''),
    url TEXT NOT NULL CHECK (url ~ '^https?://[^[:space:]]+$'),
    PRIMARY KEY (author_id, platform)
);
