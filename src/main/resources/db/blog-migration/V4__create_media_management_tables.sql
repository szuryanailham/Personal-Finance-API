CREATE TABLE media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    url TEXT NOT NULL CHECK (url ~ '^https?://[^[:space:]]+$'),
    alt_text TEXT NOT NULL DEFAULT '',
    mime_type VARCHAR(100) NOT NULL CHECK (mime_type LIKE 'image/%'),
    width INTEGER CHECK (width > 0),
    height INTEGER CHECK (height > 0),
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes >= 0),
    storage_provider VARCHAR(50) NOT NULL CHECK (btrim(storage_provider) <> ''),
    provider_asset_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (storage_provider, provider_asset_id)
);
CREATE INDEX idx_media_uploaded_by ON media(uploaded_by);
CREATE TRIGGER media_updated_at BEFORE UPDATE ON media FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE posts ADD COLUMN featured_media_id UUID REFERENCES media(id) ON DELETE RESTRICT;
CREATE INDEX idx_posts_featured_media ON posts(featured_media_id) WHERE featured_media_id IS NOT NULL;
