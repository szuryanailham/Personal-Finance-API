CREATE TABLE post_seo_metadata (
    post_id UUID PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    meta_title VARCHAR(250),
    meta_description TEXT,
    canonical_url TEXT CHECK (canonical_url ~ '^https?://[^[:space:]]+$'),
    focus_keyword VARCHAR(250),
    og_title VARCHAR(250),
    og_description TEXT,
    og_image_url TEXT CHECK (og_image_url ~ '^https?://[^[:space:]]+$'),
    robots_index BOOLEAN NOT NULL DEFAULT TRUE,
    robots_follow BOOLEAN NOT NULL DEFAULT TRUE,
    structured_data JSONB CHECK (jsonb_typeof(structured_data) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER post_seo_updated_at BEFORE UPDATE ON post_seo_metadata FOR EACH ROW EXECUTE FUNCTION set_updated_at();
