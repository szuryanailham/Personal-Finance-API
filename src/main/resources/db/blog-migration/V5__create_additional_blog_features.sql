CREATE TABLE post_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    edited_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (post_id, revision_number)
);
CREATE INDEX idx_post_revisions_editor ON post_revisions(edited_by);

-- One namespace for current and historical slugs prevents ambiguous URLs.
-- Every slug is permanently reserved to a post, including after soft deletion.
CREATE TABLE post_slug_routes (
    slug VARCHAR(200) PRIMARY KEY CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_post_slug_routes_post ON post_slug_routes(post_id);
INSERT INTO post_slug_routes(slug, post_id) SELECT slug, id FROM posts;
CREATE FUNCTION reserve_post_slug() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id UUID;
BEGIN
    INSERT INTO post_slug_routes(slug, post_id) VALUES (NEW.slug, NEW.id)
    ON CONFLICT (slug) DO NOTHING;
    SELECT post_id INTO owner_id FROM post_slug_routes WHERE slug = NEW.slug;
    IF owner_id IS DISTINCT FROM NEW.id THEN
        RAISE EXCEPTION 'Slug is reserved by another post: %', NEW.slug USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER posts_reserve_slug AFTER INSERT OR UPDATE OF slug ON posts
FOR EACH ROW EXECUTE FUNCTION reserve_post_slug();
