ALTER TABLE media
    ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (lifecycle_status IN ('ACTIVE', 'DELETED')),
    ADD COLUMN unused_since TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_cleanup_at TIMESTAMPTZ,
    ADD COLUMN last_cleanup_error TEXT,
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE post_media (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE RESTRICT,
    PRIMARY KEY (post_id, media_id)
);
CREATE INDEX idx_post_media_media ON post_media(media_id);
CREATE INDEX idx_media_cleanup ON media(unused_since, next_cleanup_at)
    WHERE lifecycle_status='ACTIVE' AND storage_provider='imagekit';

-- Conservative backfill also protects old HTML until its next save.
INSERT INTO post_media(post_id, media_id)
SELECT p.id,m.id FROM posts p JOIN media m
    ON p.featured_media_id=m.id OR strpos(replace(p.content_html, '&amp;', '&'),m.url)>0
WHERE p.deleted_at IS NULL;
UPDATE media SET unused_since=NULL
WHERE EXISTS (SELECT 1 FROM post_media pm WHERE pm.media_id=media.id);
