-- Keep deleted content intact while allowing its slugs to be reused.
ALTER TABLE posts DROP CONSTRAINT posts_slug_key;
CREATE UNIQUE INDEX uq_posts_active_slug ON posts(slug) WHERE deleted_at IS NULL;

-- Release current and historical routes belonging to already-deleted posts.
DELETE FROM post_slug_routes r USING posts p
WHERE r.post_id = p.id AND p.deleted_at IS NOT NULL;

CREATE OR REPLACE FUNCTION reserve_post_slug() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id UUID;
BEGIN
    IF NEW.deleted_at IS NOT NULL THEN
        DELETE FROM post_slug_routes WHERE post_id = NEW.id;
        RETURN NEW;
    END IF;

    INSERT INTO post_slug_routes(slug, post_id) VALUES (NEW.slug, NEW.id)
    ON CONFLICT (slug) DO NOTHING;
    SELECT post_id INTO owner_id FROM post_slug_routes WHERE slug = NEW.slug;
    IF owner_id IS DISTINCT FROM NEW.id THEN
        RAISE EXCEPTION 'Slug is reserved by another post: %', NEW.slug USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER posts_reserve_slug ON posts;
CREATE TRIGGER posts_reserve_slug AFTER INSERT OR UPDATE OF slug, deleted_at ON posts
FOR EACH ROW EXECUTE FUNCTION reserve_post_slug();
