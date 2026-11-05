package com.ilham.personal_finance_api.blog.repository;

import com.ilham.personal_finance_api.blog.entity.BlogAccount;

import com.ilham.personal_finance_api.blog.entity.BlogTaxonomyKind;

import com.ilham.personal_finance_api.blog.dto.BlogPostResponse;
import com.ilham.personal_finance_api.blog.dto.BlogTermPage;
import com.ilham.personal_finance_api.blog.dto.BlogTermResponse;
import com.ilham.personal_finance_api.blog.dto.BlogUserResponse;
import com.ilham.personal_finance_api.blog.dto.CreateBlogPostRequest;

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
                    INSERT INTO posts(id,author_id,title,slug,excerpt,content_html,status,published_at,reading_time_minutes)
                    VALUES (?,?,?,?,?,?,?,?,?) RETURNING created_at,updated_at
                    """, (rs, row) -> new BlogPostResponse(id, authorId, request.title(), request.slug(),
                    request.excerpt(), html, request.status(), published, minutes,
                    List.copyOf(request.categoryIds()), List.copyOf(request.tagIds()),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                    id, authorId, request.title(), request.slug(), request.excerpt(), html, request.status(),
                    published == null ? null : Timestamp.from(published), minutes);
            for (UUID category : request.categoryIds()) {
                jdbc.update("INSERT INTO post_categories(post_id,category_id) VALUES (?,?)", id, category);
            }
            for (UUID tag : request.tagIds()) {
                jdbc.update("INSERT INTO post_tags(post_id,tag_id) VALUES (?,?)", id, tag);
            }
            return result;
        });
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
