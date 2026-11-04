package com.ilham.personal_finance_api.blog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ilham.personal_finance_api.blog.BlogAuthModels.UserResponse;

public class BlogStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public BlogStore(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    record Account(UUID id, String displayName, String email, String status, String passwordHash) {}

    Optional<Account> byEmail(String email) {
        return jdbc.query("SELECT id, display_name, email, status, password_hash FROM users WHERE lower(email) = ?",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("status"), rs.getString("password_hash")), email)
                .stream().findFirst();
    }

    UserResponse create(String name, String email, String passwordHash) {
        return transactions.execute(tx -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id, display_name, email, password_hash) VALUES (?, ?, ?, ?)",
                    id, name, email, passwordHash);
            int count = jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE name = 'AUTHOR'", id);
            if (count != 1) throw new IllegalStateException("Blog AUTHOR role is missing");
            return new UserResponse(id, name, email, "ACTIVE", List.of("AUTHOR"));
        });
    }

    UserResponse profile(Account account) {
        return new UserResponse(account.id(), account.displayName(), account.email(), account.status(),
                jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ? ORDER BY r.name",
                        String.class, account.id()));
    }

    void issueSession(UUID userId, String hash, Instant now, Instant expires) {
        transactions.executeWithoutResult(tx -> {
            // Retain active sessions; remove expired sessions for this user on login.
            jdbc.update("DELETE FROM blog_auth_sessions WHERE user_id = ? AND expires_at <= ?", userId, Timestamp.from(now));
            jdbc.update("INSERT INTO blog_auth_sessions(token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                    hash, userId, Timestamp.from(now), Timestamp.from(expires));
        });
    }

    Optional<Account> session(String hash, Instant now) {
        return jdbc.query("""
                SELECT u.id, u.display_name, u.email, u.status, u.password_hash
                FROM blog_auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > ? AND u.status = 'ACTIVE'
                """, (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("display_name"),
                rs.getString("email"), rs.getString("status"), rs.getString("password_hash")),
                hash, Timestamp.from(now)).stream().findFirst();
    }

    void revoke(String hash) {
        jdbc.update("DELETE FROM blog_auth_sessions WHERE token_hash = ?", hash);
    }

    private static String termColumns(BlogTaxonomyModels.Kind kind) {
        return "id, name, slug, " + (kind == BlogTaxonomyModels.Kind.CATEGORY ? "description" : "NULL::text AS description")
                + ", created_at, updated_at";
    }

    private static final org.springframework.jdbc.core.RowMapper<BlogTaxonomyModels.TermResponse> TERM_MAPPER =
            (rs, row) -> new BlogTaxonomyModels.TermResponse(rs.getObject("id", UUID.class),
                    rs.getString("name"), rs.getString("slug"), rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    Optional<BlogTaxonomyModels.TermResponse> term(BlogTaxonomyModels.Kind kind, UUID id) {
        return jdbc.query("SELECT " + termColumns(kind) + " FROM " + kind.table + " WHERE id=?",
                TERM_MAPPER, id).stream().findFirst();
    }

    BlogTaxonomyModels.TermPage terms(BlogTaxonomyModels.Kind kind, int page, int size, String search) {
        // A literal substring search: %, _ and quotes have no wildcard/SQL meaning.
        String where = " WHERE strpos(lower(name), lower(?)) > 0 OR strpos(slug, lower(?)) > 0";
        long count = jdbc.queryForObject("SELECT count(*) FROM " + kind.table + where, Long.class, search, search);
        var items = jdbc.query("SELECT " + termColumns(kind) + " FROM " + kind.table + where
                        + " ORDER BY lower(name), id LIMIT ? OFFSET ?", TERM_MAPPER,
                search, search, size, (long) page * size);
        var paging = new com.ilham.personal_finance_api.dto.PaginationResponse();
        paging.setCurrentPage(page);
        paging.setSize(size);
        paging.setTotalItem(count);
        paging.setTotalPage((int) Math.ceil((double) count / size));
        return new BlogTaxonomyModels.TermPage(items, paging);
    }

    BlogTaxonomyModels.TermResponse createTerm(BlogTaxonomyModels.Kind kind, String name, String slug, String description) {
        UUID id = UUID.randomUUID();
        if (kind == BlogTaxonomyModels.Kind.CATEGORY) {
            return jdbc.queryForObject("INSERT INTO categories(id,name,slug,description) VALUES (?,?,?,?) RETURNING "
                    + termColumns(kind), TERM_MAPPER, id, name, slug, description);
        }
        return jdbc.queryForObject("INSERT INTO tags(id,name,slug) VALUES (?,?,?) RETURNING "
                + termColumns(kind), TERM_MAPPER, id, name, slug);
    }

    Optional<BlogTaxonomyModels.TermResponse> updateTerm(BlogTaxonomyModels.Kind kind, UUID id,
            String name, String slug, String description) {
        if (kind == BlogTaxonomyModels.Kind.CATEGORY) {
            return jdbc.query("UPDATE categories SET name=?,slug=?,description=? WHERE id=? RETURNING "
                    + termColumns(kind), TERM_MAPPER, name, slug, description, id).stream().findFirst();
        }
        return jdbc.query("UPDATE tags SET name=?,slug=? WHERE id=? RETURNING " + termColumns(kind),
                TERM_MAPPER, name, slug, id).stream().findFirst();
    }

    boolean deleteTerm(BlogTaxonomyModels.Kind kind, UUID id) {
        return jdbc.update("DELETE FROM " + kind.table + " WHERE id=?", id) == 1;
    }

}
