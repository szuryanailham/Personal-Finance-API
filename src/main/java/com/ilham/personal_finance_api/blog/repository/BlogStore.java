package com.ilham.personal_finance_api.blog.repository;

import com.ilham.personal_finance_api.blog.entity.BlogAccount;

import com.ilham.personal_finance_api.blog.entity.BlogTaxonomyKind;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.BlogTermPage;
import com.ilham.personal_finance_api.blog.dto.BlogTermResponse;
import com.ilham.personal_finance_api.blog.dto.BlogUserResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

import com.ilham.personal_finance_api.blog.dto.BlogPostListResponse;
import com.ilham.personal_finance_api.blog.dto.BlogPostSummary;
import com.ilham.personal_finance_api.blog.dto.BlogOffsetPaging;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostListResponse;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.PublicBlogPostSummary;
import com.ilham.personal_finance_api.blog.dto.PublicBlogTerm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class BlogStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public BlogStore(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    public Optional<BlogAccount> byEmail(String email) {
        return jdbc.query("SELECT id, display_name, email, status, password_hash FROM users WHERE lower(email) = ?",
                (rs, row) -> new BlogAccount(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("status"), rs.getString("password_hash")), email)
                .stream().findFirst();
    }

    public BlogUserResponse create(String name, String email, String passwordHash) {
        return transactions.execute(tx -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id, display_name, email, password_hash) VALUES (?, ?, ?, ?)",
                    id, name, email, passwordHash);
            int count = jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE name = 'AUTHOR'", id);
            if (count != 1) throw new IllegalStateException("Blog AUTHOR role is missing");
            return new BlogUserResponse(id, name, email, "ACTIVE", List.of("AUTHOR"));
        });
    }

    public BlogUserResponse profile(BlogAccount account) {
        return new BlogUserResponse(account.id(), account.displayName(), account.email(), account.status(),
                jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ? ORDER BY r.name",
                        String.class, account.id()));
    }

    public void issueSession(UUID userId, String hash, Instant now, Instant expires) {
        transactions.executeWithoutResult(tx -> {
            // Retain active sessions; remove expired sessions for this user on login.
            jdbc.update("DELETE FROM blog_auth_sessions WHERE user_id = ? AND expires_at <= ?", userId, Timestamp.from(now));
            jdbc.update("INSERT INTO blog_auth_sessions(token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                    hash, userId, Timestamp.from(now), Timestamp.from(expires));
        });
    }

    public Optional<BlogAccount> session(String hash, Instant now) {
        return jdbc.query("""
                SELECT u.id, u.display_name, u.email, u.status, u.password_hash
                FROM blog_auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > ? AND u.status = 'ACTIVE'
                """, (rs, row) -> new BlogAccount(rs.getObject("id", UUID.class), rs.getString("display_name"),
                rs.getString("email"), rs.getString("status"), rs.getString("password_hash")),
                hash, Timestamp.from(now)).stream().findFirst();
    }

    public void revoke(String hash) {
        jdbc.update("DELETE FROM blog_auth_sessions WHERE token_hash = ?", hash);
    }

    private static String termColumns(BlogTaxonomyKind kind) {
        return "id, name, slug, " + (kind == BlogTaxonomyKind.CATEGORY ? "description" : "NULL::text AS description")
                + ", created_at, updated_at";
    }

    private static final org.springframework.jdbc.core.RowMapper<BlogTermResponse> TERM_MAPPER =
            (rs, row) -> new BlogTermResponse(rs.getObject("id", UUID.class),
                    rs.getString("name"), rs.getString("slug"), rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    public Optional<BlogTermResponse> term(BlogTaxonomyKind kind, UUID id) {
        return jdbc.query("SELECT " + termColumns(kind) + " FROM " + kind.table() + " WHERE id=?",
                TERM_MAPPER, id).stream().findFirst();
    }

    public BlogTermPage terms(BlogTaxonomyKind kind, int page, int size, String search) {
        // A literal substring search: %, _ and quotes have no wildcard/SQL meaning.
        String where = " WHERE strpos(lower(name), lower(?)) > 0 OR strpos(slug, lower(?)) > 0";
        long count = jdbc.queryForObject("SELECT count(*) FROM " + kind.table() + where, Long.class, search, search);
        var items = jdbc.query("SELECT " + termColumns(kind) + " FROM " + kind.table() + where
                        + " ORDER BY lower(name), id LIMIT ? OFFSET ?", TERM_MAPPER,
                search, search, size, (long) page * size);
        var paging = new com.ilham.personal_finance_api.dto.PaginationResponse();
        paging.setCurrentPage(page);
        paging.setSize(size);
        paging.setTotalItem(count);
        paging.setTotalPage((int) Math.ceil((double) count / size));
        return new BlogTermPage(items, paging);
    }

    public BlogTermResponse createTerm(BlogTaxonomyKind kind, String name, String slug, String description) {
        UUID id = UUID.randomUUID();
        if (kind == BlogTaxonomyKind.CATEGORY) {
            return jdbc.queryForObject("INSERT INTO categories(id,name,slug,description) VALUES (?,?,?,?) RETURNING "
                    + termColumns(kind), TERM_MAPPER, id, name, slug, description);
        }
        return jdbc.queryForObject("INSERT INTO tags(id,name,slug) VALUES (?,?,?) RETURNING "
                + termColumns(kind), TERM_MAPPER, id, name, slug);
    }

    public Optional<BlogTermResponse> updateTerm(BlogTaxonomyKind kind, UUID id,
            String name, String slug, String description) {
        if (kind == BlogTaxonomyKind.CATEGORY) {
            return jdbc.query("UPDATE categories SET name=?,slug=?,description=? WHERE id=? RETURNING "
                    + termColumns(kind), TERM_MAPPER, name, slug, description, id).stream().findFirst();
        }
        return jdbc.query("UPDATE tags SET name=?,slug=? WHERE id=? RETURNING " + termColumns(kind),
                TERM_MAPPER, name, slug, id).stream().findFirst();
    }

    public boolean deleteTerm(BlogTaxonomyKind kind, UUID id) {
        return jdbc.update("DELETE FROM " + kind.table() + " WHERE id=?", id) == 1;
    }

    public PublicBlogPostListResponse publicPosts(long skip, int limit, Instant now) {
        var read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(tx -> {
            var timestamp = Timestamp.from(now);
            long total = jdbc.queryForObject("SELECT count(*) FROM posts WHERE status='PUBLISHED' "
                    + "AND deleted_at IS NULL AND published_at<=?", Long.class, timestamp);
            var items = jdbc.query("""
                    SELECT id,title,slug,excerpt,published_at,reading_time_minutes FROM posts
                    WHERE status='PUBLISHED' AND deleted_at IS NULL AND published_at<=?
                    ORDER BY published_at DESC,id DESC LIMIT ? OFFSET ?
                    """, (rs, row) -> new PublicBlogPostSummary(rs.getObject("id", UUID.class),
                    rs.getString("title"), rs.getString("slug"), rs.getString("excerpt"),
                    rs.getTimestamp("published_at").toInstant(), rs.getObject("reading_time_minutes", Integer.class)),
                    timestamp, limit, skip);
            return new PublicBlogPostListResponse(items,
                    new BlogOffsetPaging(skip, limit, total, skip < total && items.size() < total - skip));
        });
    }

    public Optional<PublicBlogPostResponse> publicPost(String slug, Instant now) {
        var read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(tx -> jdbc.query("""
                SELECT id,title,slug,excerpt,content_html,published_at,reading_time_minutes,updated_at
                FROM posts WHERE slug=? AND status='PUBLISHED' AND deleted_at IS NULL AND published_at<=?
                """, (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new PublicBlogPostResponse(id, rs.getString("title"), rs.getString("slug"),
                            rs.getString("excerpt"), rs.getString("content_html"), rs.getTimestamp("published_at").toInstant(),
                            rs.getObject("reading_time_minutes", Integer.class), publicTerms(BlogTaxonomyKind.CATEGORY, id),
                            publicTerms(BlogTaxonomyKind.TAG, id), rs.getTimestamp("updated_at").toInstant());
                }, slug, Timestamp.from(now)).stream().findFirst());
    }

    private List<PublicBlogTerm> publicTerms(BlogTaxonomyKind kind, UUID postId) {
        String link = kind == BlogTaxonomyKind.CATEGORY ? "post_categories" : "post_tags";
        String column = kind == BlogTaxonomyKind.CATEGORY ? "category_id" : "tag_id";
        return jdbc.query("SELECT t.id,t.name,t.slug FROM " + kind.table() + " t JOIN " + link
                        + " l ON l." + column + "=t.id WHERE l.post_id=? ORDER BY lower(t.name),t.id",
                (rs, row) -> new PublicBlogTerm(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug")), postId);
    }

    public BlogPostListResponse posts(long skip, int limit) {
        // One repeatable-read snapshot keeps count and page consistent during concurrent writes.
        var read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(tx -> {
            long total = jdbc.queryForObject("SELECT count(*) FROM posts WHERE deleted_at IS NULL", Long.class);
            var items = jdbc.query("""
                    SELECT p.id,p.author_id,p.title,p.slug,p.excerpt,m.url AS hero_image,p.status,
                           p.published_at,p.reading_time_minutes,p.created_at,p.updated_at
                    FROM posts p LEFT JOIN media m ON m.id=p.featured_media_id
                    WHERE p.deleted_at IS NULL
                    ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?
                    """, (rs, row) -> new BlogPostSummary(rs.getObject("id", UUID.class),
                    rs.getObject("author_id", UUID.class), rs.getString("title"), rs.getString("slug"),
                    rs.getString("excerpt"), rs.getString("hero_image"), rs.getString("status"),
                    rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                    rs.getObject("reading_time_minutes", Integer.class),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), limit, skip);
            return new BlogPostListResponse(items,
                    new BlogOffsetPaging(skip, limit, total, skip < total && items.size() < total - skip));
        });
    }

    public BlogPostResponse createPost(UUID authorId, CreateBlogPostRequest request,
            String html, int minutes, Instant now) {
        return transactions.execute(tx -> {
            // Lock referenced rows until commit so taxonomy deletion cannot race the insert.
            for (UUID id : request.categoryIds().stream().sorted().toList()) {
                requireTerm("categories", id);
            }
            for (UUID id : request.tagIds().stream().sorted().toList()) {
                requireTerm("tags", id);
            }
            UUID id = UUID.randomUUID();
            Instant published = request.status().equals("PUBLISHED") ? now : null;
            var result = jdbc.queryForObject("""
                    INSERT INTO posts(id,author_id,title,slug,excerpt,content_html,status,published_at,reading_time_minutes,featured_media_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING created_at,updated_at
                    """, (rs, row) -> new BlogPostResponse(id, authorId, request.title(), request.slug(),
                    request.excerpt(), html, request.status(), published, minutes,
                    List.copyOf(request.categoryIds()), List.copyOf(request.tagIds()),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                    id, authorId, request.title(), request.slug(), request.excerpt(), html, request.status(),
                    published == null ? null : Timestamp.from(published), minutes, request.featuredMediaId());
            for (UUID category : request.categoryIds()) {
                jdbc.update("INSERT INTO post_categories(post_id,category_id) VALUES (?,?)", id, category);
            }
            for (UUID tag : request.tagIds()) {
                jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES (?,?)", id, tag);
            }
            return result;
        });
    }

    public Optional<BlogPostResponse> post(UUID id) {
        var read = new TransactionTemplate(transactions.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(tx -> jdbc.query("SELECT * FROM posts WHERE id=? AND deleted_at IS NULL",
                (rs, row) -> new BlogPostResponse(rs.getObject("id", UUID.class),
                        rs.getObject("author_id", UUID.class), rs.getString("title"), rs.getString("slug"),
                        rs.getString("excerpt"), rs.getString("content_html"), rs.getString("status"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                        rs.getObject("reading_time_minutes", Integer.class) == null ? 1 : rs.getInt("reading_time_minutes"),
                        postTermIds("post_categories", "category_id", id), postTermIds("post_tags", "tag_id", id),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id)
                .stream().findFirst());
    }

    public void softDeletePost(UUID id, Instant now, java.util.function.BiConsumer<UUID, String> authorize) {
        transactions.executeWithoutResult(tx -> {
            record Target(UUID authorId, String status) {}
            var target = jdbc.query("SELECT author_id,status FROM posts WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                    (rs, row) -> new Target(rs.getObject("author_id", UUID.class), rs.getString("status")), id)
                    .stream().findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Blog post not found"));
            authorize.accept(target.authorId(), target.status());
            jdbc.update("UPDATE posts SET deleted_at=?,scheduled_at=NULL WHERE id=?", Timestamp.from(now), id);
        });
    }

    public BlogPostResponse updatePost(UUID id, java.util.function.Function<BlogPostResponse, BlogPostResponse> patch) {
        return transactions.execute(tx -> {
            var current = jdbc.query("SELECT * FROM posts WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                    (rs, row) -> new BlogPostResponse(rs.getObject("id", UUID.class),
                            rs.getObject("author_id", UUID.class), rs.getString("title"), rs.getString("slug"),
                            rs.getString("excerpt"), rs.getString("content_html"), rs.getString("status"),
                            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                            rs.getObject("reading_time_minutes", Integer.class) == null ? 1 : rs.getInt("reading_time_minutes"),
                            postTermIds("post_categories", "category_id", id), postTermIds("post_tags", "tag_id", id),
                            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id)
                    .stream().findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Blog post not found"));
            var updated = patch.apply(current);
            boolean categoriesChanged = !new java.util.HashSet<>(current.categoryIds()).equals(new java.util.HashSet<>(updated.categoryIds()));
            boolean tagsChanged = !new java.util.HashSet<>(current.tagIds()).equals(new java.util.HashSet<>(updated.tagIds()));
            if (categoriesChanged) for (UUID term : updated.categoryIds().stream().sorted().toList()) requireTerm("categories", term);
            if (tagsChanged) for (UUID term : updated.tagIds().stream().sorted().toList()) requireTerm("tags", term);
            var times = jdbc.queryForObject("""
                    UPDATE posts SET title=?,slug=?,excerpt=?,content_html=?,status=?,published_at=?,reading_time_minutes=?,
                    scheduled_at=CASE WHEN ?='DRAFT' THEN scheduled_at ELSE NULL END
                    WHERE id=? RETURNING updated_at
                    """, (rs, row) -> rs.getTimestamp("updated_at").toInstant(), updated.title(), updated.slug(),
                    updated.excerpt(), updated.contentHtml(), updated.status(),
                    updated.publishedAt() == null ? null : Timestamp.from(updated.publishedAt()),
                    updated.readingTimeMinutes(), updated.status(), id);
            if (categoriesChanged) {
                jdbc.update("DELETE FROM post_categories WHERE post_id=?", id);
                for (UUID term : updated.categoryIds()) jdbc.update("INSERT INTO post_categories(post_id,category_id) VALUES (?,?)", id, term);
            }
            if (tagsChanged) {
                jdbc.update("DELETE FROM post_tags WHERE post_id=?", id);
                for (UUID term : updated.tagIds()) jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES (?,?)", id, term);
            }
            return new BlogPostResponse(id, updated.authorId(), updated.title(), updated.slug(), updated.excerpt(),
                    updated.contentHtml(), updated.status(), updated.publishedAt(), updated.readingTimeMinutes(),
                    updated.categoryIds(), updated.tagIds(), current.createdAt(), times);
        });
    }

    private List<UUID> postTermIds(String table, String column, UUID postId) {
        return jdbc.query("SELECT " + column + " FROM " + table + " WHERE post_id=? ORDER BY " + column,
                (rs, row) -> rs.getObject(1, UUID.class), postId);
    }

    private void requireTerm(String table, UUID id) {
        // table is an internal constant, never supplied by the client.
        if (jdbc.query("SELECT id FROM " + table + " WHERE id=? FOR KEY SHARE",
                (rs, row) -> rs.getObject("id", UUID.class), id).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Category or tag does not exist: " + id);
        }
    }

}
