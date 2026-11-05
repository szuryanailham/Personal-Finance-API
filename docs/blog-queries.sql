-- Named :parameters are application bindings, not literal psql syntax.
-- 1. Public feed: use keyset pagination after the first page.
SELECT p.id, p.title, p.slug, p.excerpt, p.published_at, m.url AS featured_image_url
FROM posts p LEFT JOIN media m ON m.id = p.featured_media_id
WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP
ORDER BY p.published_at DESC, p.id DESC LIMIT :page_size;
-- Subsequent pages add: AND (p.published_at, p.id) < (:cursor_time, :cursor_id)

-- 2. Category.
SELECT p.id, p.title, p.slug, p.excerpt, p.published_at
FROM posts p JOIN post_categories pc ON pc.post_id = p.id
JOIN categories c ON c.id = pc.category_id
WHERE c.slug = :category_slug AND p.status = 'PUBLISHED'
  AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP
ORDER BY p.published_at DESC, p.id DESC LIMIT :page_size;

-- 3. Tag.
SELECT p.id, p.title, p.slug, p.excerpt, p.published_at
FROM posts p JOIN post_tags pt ON pt.post_id = p.id JOIN tags t ON t.id = pt.tag_id
WHERE t.slug = :tag_slug AND p.status = 'PUBLISHED'
  AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP
ORDER BY p.published_at DESC, p.id DESC LIMIT :page_size;

-- 4. Detail, including historical slug resolution.
-- If requested_slug differs from p.slug, return HTTP 301 to /blog/{p.slug}.
-- No row means HTTP 404 (including deleted, archived and unpublished posts).
SELECT p.*, u.display_name, m.url AS featured_image_url, m.alt_text,
       s.meta_title, s.meta_description, s.canonical_url, s.focus_keyword,
       s.og_title, s.og_description, s.og_image_url,
       s.robots_index, s.robots_follow, s.structured_data
FROM post_slug_routes r JOIN posts p ON p.id = r.post_id
JOIN users u ON u.id = p.author_id
LEFT JOIN media m ON m.id = p.featured_media_id
LEFT JOIN post_seo_metadata s ON s.post_id = p.id
WHERE r.slug = :requested_slug AND p.status = 'PUBLISHED'
  AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP;

-- 5. Related posts: shared categories/tags, no dedicated relationship table.
WITH matches AS (
    SELECT pc.post_id, 2 AS weight FROM post_categories pc
    JOIN post_categories source ON source.category_id = pc.category_id
    WHERE source.post_id = :post_id AND pc.post_id <> :post_id
    UNION ALL
    SELECT pt.post_id, 1 AS weight FROM post_tags pt
    JOIN post_tags source ON source.tag_id = pt.tag_id
    WHERE source.post_id = :post_id AND pt.post_id <> :post_id
), scores AS (SELECT post_id, SUM(weight) AS score FROM matches GROUP BY post_id)
SELECT p.id, p.title, p.slug, p.excerpt, s.score
FROM scores s JOIN posts p ON p.id = s.post_id
WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP
ORDER BY s.score DESC, p.published_at DESC, p.id DESC LIMIT :page_size;

-- 6. Editorial publication report. Visitor analytics use external tools.
-- Publication volume by month (Asia/Jakarta business timezone).
SELECT date_trunc('month', published_at AT TIME ZONE 'Asia/Jakarta') AS month,
       COUNT(*) AS posts_published
FROM posts WHERE published_at <= CURRENT_TIMESTAMP AND deleted_at IS NULL
GROUP BY 1 ORDER BY 1;
