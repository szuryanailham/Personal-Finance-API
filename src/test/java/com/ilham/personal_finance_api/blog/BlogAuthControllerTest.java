package com.ilham.personal_finance_api.blog;

import com.ilham.personal_finance_api.blog.service.BlogAuthService;

import com.ilham.personal_finance_api.AbstractIntegrationTest;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.blog.enabled=true", "app.blog.registration-key=test-registration-key"})
@AutoConfigureMockMvc
class BlogAuthControllerTest extends AbstractIntegrationTest {
    static final PostgreSQLContainer BLOG = new PostgreSQLContainer("postgres:16-alpine");
    static { BLOG.start(); }

    @DynamicPropertySource
    static void blogProperties(DynamicPropertyRegistry registry) {
        registry.add("app.blog.datasource.url", BLOG::getJdbcUrl);
        registry.add("app.blog.datasource.username", BLOG::getUsername);
        registry.add("app.blog.datasource.password", BLOG::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository financeUsers;
    @Autowired Flyway financeFlyway;
    @Autowired @Qualifier("blogDataSource") HikariDataSource blogDataSource;
    @MockitoBean Clock clock;
    JdbcTemplate jdbc;
    String email;
    static final String PASSWORD = " valid password 123 ";
    static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(blogDataSource);
        email = UUID.randomUUID() + "@example.com";
        when(clock.instant()).thenReturn(NOW);
    }

    ResultActions register(String key, Map<String, String> body) throws Exception {
        var request = post("/api/blog/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (key != null) request.header("X-Blog-Registration-Key", key);
        return mvc.perform(request);
    }

    Map<String,String> registration() {
        return Map.of("displayName", "CMS Author", "email", email, "password", PASSWORD);
    }

    void create() throws Exception {
        register("test-registration-key", registration()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("AUTHOR"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    ResultActions login(String candidate, String password) throws Exception {
        return mvc.perform(post("/api/blog/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", candidate, "password", password))));
    }

    String token() throws Exception {
        var result = login(email.toUpperCase(java.util.Locale.ROOT), PASSWORD)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("token").asString();
    }

    @Test void editsOwnProfileAndPreservesOmittedFields() throws Exception {
        create();
        String token = token();
        mvc.perform(patch("/api/blog/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":" Updated Admin ","photoProfile":"https://example.com/avatar.png",
                         "description":"Financial writer","socialMedia":{"instagram":"https://instagram.com/admin"}}
                        """))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.displayName").value("Updated Admin"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("AUTHOR"));
        mvc.perform(patch("/api/blog/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":null,\"socialMedia\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("Updated Admin"))
                .andExpect(jsonPath("$.data.photoProfile").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.description").isEmpty())
                .andExpect(jsonPath("$.data.socialMedia").isEmpty());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("Updated Admin"));
    }

    @Test void profileUpdateRequiresSessionAndValidInput() throws Exception {
        mvc.perform(patch("/api/blog/auth/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Admin\"}")).andExpect(status().isUnauthorized());
        create();
        String token = token();
        for (String body : new String[]{"{}", "{\"displayName\":null}", "{\"displayName\":\"  \"}",
                "{\"photoProfile\":\"javascript:alert(1)\"}", "{\"socialMedia\":{\"website\":\"invalid\"}}"}) {
            mvc.perform(patch("/api/blog/auth/me").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("CMS Author"));
    }

    @Test void registersOnlyInBlogAndKeepsFinanceFlyway() throws Exception {
        create();
        assertTrue(financeUsers.findByEmail(email).isEmpty());
        assertEquals("11", financeFlyway.info().current().getVersion().toString());
        assertEquals(11, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertNotEquals(PASSWORD, stored);
        assertTrue(BCrypt.checkpw(PASSWORD, stored));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='post_daily_stats'", Integer.class));
    }

    @Test void defaultRootAdminCanLogin() throws Exception {
        login("superadmin@gmail.com", "superadmin2026!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("superadmin@gmail.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"));
    }

    @Test void rootAdminMigrationUpgradesExistingAccountAndPreservesIdentityAndRoles() {
        String schema = "admin_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                BLOG.getJdbcUrl() + (BLOG.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                BLOG.getUsername(), BLOG.getPassword());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").target("10").load().migrate();
        var legacy = new JdbcTemplate(source);
        UUID id = UUID.randomUUID();
        legacy.update("INSERT INTO users(id,display_name,email,password_hash,status) VALUES (?,'Existing','SuperAdmin@gmail.com','old-hash','SUSPENDED')", id);
        legacy.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='AUTHOR'", id);
        var flyway = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").load();
        flyway.migrate();
        assertEquals(id, legacy.queryForObject("SELECT id FROM users WHERE lower(email)='superadmin@gmail.com'", UUID.class));
        assertEquals("Existing", legacy.queryForObject("SELECT display_name FROM users WHERE id=?", String.class, id));
        assertEquals("ACTIVE", legacy.queryForObject("SELECT status FROM users WHERE id=?", String.class, id));
        assertTrue(BCrypt.checkpw("superadmin2026!", legacy.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, id)));
        assertEquals(java.util.List.of("ADMIN", "AUTHOR"), legacy.queryForList(
                "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.name", String.class, id));
        legacy.update("UPDATE users SET password_hash='changed-after-migration' WHERE id=?", id);
        flyway.migrate();
        assertEquals("changed-after-migration", legacy.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, id));
    }

    @Test void requiresRegistrationKeyAndCannotChooseAdmin() throws Exception {
        register(null, registration()).andExpect(status().isForbidden());
        register("wrong", registration()).andExpect(status().isForbidden());
        var body = new java.util.HashMap<>(registration());
        body.put("role", "ADMIN");
        register("test-registration-key", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("AUTHOR"));
    }

    @Test void duplicateEmailIgnoresCase() throws Exception {
        create();
        var body = new java.util.HashMap<>(registration());
        body.put("email", email.toUpperCase(java.util.Locale.ROOT));
        register("test-registration-key", body).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email)=?", Integer.class, email));
    }

    @Test void rejectsInvalidInputsAndUtf8Truncation() throws Exception {
        for (String password : new String[]{"short", " ".repeat(12), "界".repeat(25)}) {
            register("test-registration-key", Map.of("displayName", "Author", "email", email, "password", password))
                    .andExpect(status().isBadRequest());
        }
        register("test-registration-key", Map.of("displayName", " ", "email", email, "password", PASSWORD))
                .andExpect(status().isBadRequest());
        login("bad-email", PASSWORD).andExpect(status().isBadRequest());
        mvc.perform(post("/api/blog/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void tokenIsHashedAndLogoutRevokesIt() throws Exception {
        create();
        String token = token();
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(token).length);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM blog_auth_sessions WHERE token_hash=?",
                Integer.class, BlogAuthService.hash(token)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM blog_auth_sessions WHERE token_hash=?", Integer.class, token));
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
        mvc.perform(post("/api/blog/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void rejectsExpiredInactiveAndUnknownSessions() throws Exception {
        create();
        String token = token();
        when(clock.instant()).thenReturn(NOW.plusSeconds(8 * 3600));
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        when(clock.instant()).thenReturn(NOW);
        for (String state : new String[]{"INACTIVE", "SUSPENDED"}) {
            jdbc.update("UPDATE users SET status=? WHERE email=?", state, email);
            login(email, PASSWORD).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/blog/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test void logoutPreservesOtherSessionsAndLoginCleansUpExpiredSessions() throws Exception {
        create();
        String first = token();
        String second = token();
        assertNotEquals(first, second);
        mvc.perform(post("/api/blog/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
        when(clock.instant()).thenReturn(NOW.plusSeconds(8 * 3600));
        String replacement = token();
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM blog_auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE u.email=?
                """, Integer.class, email));
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + second))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + replacement))
                .andExpect(status().isOk());
    }

    @Test void meReadsCurrentRolesAndAcceptsCaseInsensitiveBearerScheme() throws Exception {
        create();
        String token = token();
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_id)
                SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email=? AND r.name='EDITOR'
                """, email);
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "bEaReR " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("AUTHOR"))
                .andExpect(jsonPath("$.data.roles[1]").value("EDITOR"));
        for (String authorization : new String[]{"Basic " + token, "Bearer ", "Bearer " + token + " extra"}) {
            mvc.perform(get("/api/blog/auth/me").header("Authorization", authorization))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void credentialsAreNotInterchangeableWithFinance() throws Exception {
        create();
        login(email, "wrong password").andExpect(status().isUnauthorized());
        login("unknown@example.com", PASSWORD).andExpect(status().isUnauthorized());
        String token = token();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        var financeUser = new com.ilham.personal_finance_api.entity.User();
        financeUser.setFirstName("Finance");
        financeUser.setLastName("Only");
        financeUser.setEmail(UUID.randomUUID() + "@example.com");
        financeUser.setPassword(BCrypt.hashpw(PASSWORD, BCrypt.gensalt()));
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        String financeToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        financeUser.setToken(financeToken);
        financeUser.setTokenExpiredAt(System.currentTimeMillis() + 60000);
        financeUsers.saveAndFlush(financeUser);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + financeToken)).andExpect(status().isOk());
        mvc.perform(get("/api/blog/auth/me").header("Authorization", "Bearer " + financeToken)).andExpect(status().isUnauthorized());
        login(financeUser.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
    }

    void grant(String role) {
        jdbc.update("""
                INSERT INTO user_roles(user_id,role_id)
                SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email=? AND r.name=?
                ON CONFLICT DO NOTHING
                """, email, role);
    }

    String createTerm(String path, String token, String slug) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", " Personal Finance ", "slug", slug,
                                "description", "Money tips"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Personal Finance"))
                .andExpect(jsonPath("$.data.slug").value(slug))
                .andExpect(jsonPath("$.data.createdAt").exists()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asString();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"categories", "tags"})
    void taxonomyCrudAndPagination(String resource) throws Exception {
        create();
        grant(resource.equals("categories") ? "ADMIN" : "EDITOR");
        String token = token();
        String path = "/api/blog/" + resource;
        String slug = "finance-" + UUID.randomUUID();
        String id = createTerm(path, token, slug);
        mvc.perform(get(path + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value(slug));
        mvc.perform(get(path).param("search", slug).param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.paging.totalItem").value(1))
                .andExpect(jsonPath("$.paging.totalPage").value(1));
        mvc.perform(get(path).param("search", slug).param("page", "1").param("size", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put(path + "/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Updated", "slug", slug + "-updated",
                                "description", "Updated description"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated"))
                .andExpect(jsonPath("$.data.slug").value(slug + "-updated"));
        if (resource.equals("categories")) {
            mvc.perform(get(path + "/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.data.description").value("Updated description"));
        }
        mvc.perform(delete(path + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(resource.equals("categories")
                        ? "Category deleted successfully" : "Tag deleted successfully"));
        mvc.perform(get(path + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"categories", "tags"})
    void taxonomyRequiresCmsSessionAndElevatedRoleForWrites(String resource) throws Exception {
        String path = "/api/blog/" + resource;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        create();
        String token = token();
        mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        String body = "{\"name\":\"Finance\",\"slug\":\"finance\"}";
        mvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String id = UUID.randomUUID().toString();
        mvc.perform(put(path + "/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(path + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        grant("EDITOR");
        String slug = "role-" + UUID.randomUUID();
        String existing = createTerm(path, token, slug);
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?) "
                + "AND role_id=(SELECT id FROM roles WHERE name='EDITOR')", email);
        mvc.perform(delete(path + "/" + existing).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        when(clock.instant()).thenReturn(NOW.plusSeconds(8 * 3600));
        mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"categories", "tags"})
    void taxonomyValidatesInputAndReturnsConflictsAndNotFound(String resource) throws Exception {
        create();
        grant("EDITOR");
        String token = token();
        String path = "/api/blog/" + resource;
        for (String slug : new String[]{"Bad Slug", "double--dash", "-leading", "", "a".repeat(151)}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", "Name", "slug", slug))))
                    .andExpect(status().isBadRequest());
        }
        for (String name : new String[]{" ", "n".repeat(101)}) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", name, "slug", "valid"))))
                    .andExpect(status().isBadRequest());
        }
        for (String query : new String[]{"page=-1", "size=0", "size=101"}) {
            String[] pair = query.split("=");
            mvc.perform(get(path).param(pair[0], pair[1]).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path + "/bad-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        String missing = path + "/" + UUID.randomUUID();
        mvc.perform(get(missing).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(delete(missing).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(put(missing).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Name\",\"slug\":\"valid\"}")).andExpect(status().isNotFound());
        String slug = "unique-" + UUID.randomUUID();
        createTerm(path, token, slug);
        mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Other", "slug", slug))))
                .andExpect(status().isConflict());
        String other = createTerm(path, token, slug + "-other");
        mvc.perform(put(path + "/" + other).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Other", "slug", slug))))
                .andExpect(status().isConflict());
        mvc.perform(get(path).param("search", "' OR 1=1 --%").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paging.totalItem").value(0));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"categories", "tags"})
    void taxonomyCannotDeleteTermUsedByPost(String resource) throws Exception {
        create();
        grant("ADMIN");
        String token = token();
        String path = "/api/blog/" + resource;
        String slug = "linked-" + UUID.randomUUID();
        UUID term = UUID.fromString(createTerm(path, token, slug));
        UUID postId = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,author_id,title,slug) SELECT ?,id,'Post',? FROM users WHERE email=?",
                postId, slug, email);
        String link = resource.equals("categories") ? "post_categories" : "post_tags";
        String column = resource.equals("categories") ? "category_id" : "tag_id";
        jdbc.update("INSERT INTO " + link + "(post_id," + column + ") VALUES (?,?)", postId, term);
        mvc.perform(delete(path + "/" + term).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mvc.perform(get(path + "/" + term).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    Map<String, Object> postBody(String token) throws Exception {
        grant("EDITOR");
        String category = createTerm("/api/blog/categories", token, "cat-" + UUID.randomUUID());
        String tag = createTerm("/api/blog/tags", token, "tag-" + UUID.randomUUID());
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?) "
                + "AND role_id=(SELECT id FROM roles WHERE name='EDITOR')", email);
        var body = new java.util.HashMap<String, Object>();
        body.put("title", " Budgeting ");
        body.put("slug", "budget-" + UUID.randomUUID());
        body.put("contentHtml", "<p>Hello <strong>Artoku</strong></p><script>alert(1)</script>"
                + "<a href='javascript:alert(1)' onclick='alert(1)'>link</a>");
        body.put("categoryIds", java.util.List.of(category));
        body.put("tagIds", java.util.List.of(tag));
        return body;
    }

    ResultActions postArticle(String token, Map<String, Object> body) throws Exception {
        var request = post("/api/blog/posts").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    @Test void createsDraftWithTaxonomyAndSanitizedContent() throws Exception {
        create();
        String token = token();
        var body = postBody(token);
        var response = postArticle(token, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Budgeting"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.readingTimeMinutes").value(1))
                .andReturn();
        var data = json.readTree(response.getResponse().getContentAsString()).get("data");
        UUID id = UUID.fromString(data.get("id").asString());
        assertFalse(data.get("contentHtml").asString().contains("script"));
        assertFalse(data.get("contentHtml").asString().contains("onclick"));
        assertTrue(data.get("contentHtml").asString().contains("<strong>Artoku</strong>"));
        assertEquals(email, jdbc.queryForObject("SELECT u.email FROM posts p JOIN users u ON u.id=p.author_id WHERE p.id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_categories WHERE post_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_tags WHERE post_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_slug_routes WHERE post_id=?", Integer.class, id));
        postArticle(token, body).andExpect(status().isConflict());
    }

    @Test void publishingRequiresEditorAndSetsPublicationTime() throws Exception {
        create();
        String token = token();
        var body = postBody(token);
        body.put("status", "PUBLISHED");
        postArticle(null, body).andExpect(status().isUnauthorized());
        postArticle("invalid", body).andExpect(status().isUnauthorized());
        postArticle(token, body).andExpect(status().isForbidden());
        grant("EDITOR");
        postArticle(token, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publishedAt").value(NOW.toString()));
    }

    @Test void invalidTaxonomyDoesNotLeavePartialPost() throws Exception {
        create();
        String token = token();
        var body = postBody(token);
        body.put("tagIds", java.util.List.of(UUID.randomUUID().toString()));
        postArticle(token, body).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM posts WHERE slug=?", Integer.class, body.get("slug")));
        var ids = body.get("categoryIds");
        body.put("tagIds", java.util.List.of());
        body.put("categoryIds", java.util.List.of());
        postArticle(token, body).andExpect(status().isBadRequest());
        body.put("categoryIds", ids);
        body.put("contentHtml", "<script>alert(1)</script>");
        postArticle(token, body).andExpect(status().isBadRequest());
        body.put("contentHtml", "<p>Valid</p>");
        body.put("status", "ARCHIVED");
        postArticle(token, body).andExpect(status().isBadRequest());
        body.put("status", "DRAFT");
        postArticle(token, body).andExpect(status().isCreated());
    }

    @Test void listsCmsPostsWithOffsetPaginationAndStableOrder() throws Exception {
        create();
        String token = token();
        long baseline = jdbc.queryForObject("SELECT count(*) FROM posts WHERE deleted_at IS NULL", Long.class);
        UUID author = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
        UUID first = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        UUID second = UUID.fromString("ffffffff-ffff-4fff-bfff-fffffffffffe");
        UUID deleted = UUID.randomUUID();
        // Equal timestamps exercise deterministic UUID tie breaking; use future dates to precede other fixtures.
        for (UUID id : java.util.List.of(first, second, deleted)) {
            jdbc.update("""
                    INSERT INTO posts(id,author_id,title,slug,status,published_at,created_at,deleted_at)
                    VALUES (?,?,'CMS list',?,?,CURRENT_TIMESTAMP,'2099-01-01T00:00:00Z',?)
                    """, id, author, "list-" + id, id.equals(first) ? "DRAFT" : "PUBLISHED",
                    id.equals(deleted) ? java.sql.Timestamp.from(NOW) : null);
        }
        try {
            mvc.perform(get("/api/blog/posts").param("limit", "1").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(first.toString()))
                    .andExpect(jsonPath("$.data[0].authorName").value("CMS Author"))
                    .andExpect(jsonPath("$.data[0].authorId").doesNotExist())
                    .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                    .andExpect(jsonPath("$.data[0].contentHtml").doesNotExist())
                    .andExpect(jsonPath("$.paging.skip").value(0))
                    .andExpect(jsonPath("$.paging.limit").value(1))
                    .andExpect(jsonPath("$.paging.totalItem").value(baseline + 2))
                    .andExpect(jsonPath("$.paging.hasMore").value(true));
            mvc.perform(get("/api/blog/posts").param("skip", "1").param("limit", "1")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(second.toString()))
                    .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));
            mvc.perform(get("/api/blog/posts").param("skip", String.valueOf(baseline + 2))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0))
                    .andExpect(jsonPath("$.paging.hasMore").value(false));
            mvc.perform(get("/api/blog/posts").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.paging.limit").value(20));
        } finally {
            for (UUID id : java.util.List.of(first, second, deleted)) jdbc.update("DELETE FROM posts WHERE id=?", id);
        }
    }

    @Test void listCmsPostsRequiresCmsRoleAndValidPagination() throws Exception {
        mvc.perform(get("/api/blog/posts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog/posts").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        create();
        String token = token();
        for (String query : new String[]{"skip=-1", "limit=0", "limit=101", "skip=abc", "limit=abc"}) {
            String[] pair = query.split("=");
            mvc.perform(get("/api/blog/posts").param(pair[0], pair[1]).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        mvc.perform(get("/api/blog/posts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    String draftId(String token) throws Exception {
        var result = postArticle(token, postBody(token)).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asString();
    }

    String articleSlug(String idOrSlug) {
        try {
            var slugs = jdbc.queryForList("SELECT slug FROM posts WHERE id=?", String.class, UUID.fromString(idOrSlug));
            return slugs.isEmpty() ? idOrSlug : slugs.getFirst();
        } catch (IllegalArgumentException ex) {
            return idOrSlug;
        }
    }

    @Test void detailEditAndDeleteUseCurrentSlug() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        String oldSlug = articleSlug(id);
        String newSlug = "renamed-" + UUID.randomUUID();
        detailArticle(token, oldSlug).andExpect(status().isOk());
        mvc.perform(get("/api/blog/posts/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/blog/posts/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"By ID\"}"))
                .andExpect(status().isNotFound());
        patchArticle(token, oldSlug, json.writeValueAsString(Map.of("slug", newSlug)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value(newSlug));
        detailArticle(token, oldSlug).andExpect(status().isNotFound());
        detailArticle(token, newSlug).andExpect(status().isOk());
        patchArticle(token, oldSlug, "{\"title\":\"Old slug\"}").andExpect(status().isNotFound());
        deleteArticle(token, oldSlug).andExpect(status().isNotFound());
        deleteArticle(token, newSlug).andExpect(status().isOk());
        deleteArticle(token, newSlug).andExpect(status().isNotFound());
    }

    ResultActions patchArticle(String token, String id, String body) throws Exception {
        var request = patch("/api/blog/posts/" + articleSlug(id)).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    @Test void patchesOnlySuppliedFieldsAndCanClearExcerptAndTags() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        UUID uuid = UUID.fromString(id);
        String html = jdbc.queryForObject("SELECT content_html FROM posts WHERE id=?", String.class, uuid);
        patchArticle(token, id, json.writeValueAsString(Map.of("title", " New title ", "excerpt", "Summary")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("New title"))
                .andExpect(jsonPath("$.data.contentHtml").value(html))
                .andExpect(jsonPath("$.data.categoryIds.length()").value(1))
                .andExpect(jsonPath("$.data.tagIds.length()").value(1));
        patchArticle(token, id, "{\"excerpt\":null,\"tagIds\":[]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.excerpt").isEmpty())
                .andExpect(jsonPath("$.data.tagIds.length()").value(0))
                .andExpect(jsonPath("$.data.categoryIds.length()").value(1));
        patchArticle(token, id, "{\"contentHtml\":\"<p>Updated</p><script>alert(1)</script>\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentHtml").value("<p>Updated</p>"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM post_tags WHERE post_id=?", Integer.class, uuid));
        grant("EDITOR");
        String replacement = createTerm("/api/blog/categories", token, "replacement-" + UUID.randomUUID());
        patchArticle(token, id, json.writeValueAsString(Map.of("categoryIds", java.util.List.of(replacement))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.categoryIds[0]").value(replacement));
        assertEquals(UUID.fromString(replacement), jdbc.queryForObject(
                "SELECT category_id FROM post_categories WHERE post_id=?", UUID.class, uuid));
        patchArticle(token, id, json.writeValueAsString(Map.of("categoryIds", java.util.List.of(replacement, replacement))))
                .andExpect(status().isBadRequest());
    }

    @Test void patchValidatesInputAndRollsBackInvalidReferencesAndConflicts() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        for (String body : new String[]{"{}", "{\"title\":null}", "{\"title\":\" \"}",
                "{\"categoryIds\":[]}", "{\"tagIds\":null}", "{\"status\":\"INVALID\"}",
                "{\"contentHtml\":\"<script>alert(1)</script>\"}"}) {
            patchArticle(token, id, body).andExpect(status().isBadRequest());
        }
        patchArticle(token, id, json.writeValueAsString(Map.of("title", "Must roll back",
                        "categoryIds", java.util.List.of(UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        assertEquals("Budgeting", jdbc.queryForObject("SELECT title FROM posts WHERE id=?", String.class, UUID.fromString(id)));
        String other = draftId(token);
        String slug = jdbc.queryForObject("SELECT slug FROM posts WHERE id=?", String.class, UUID.fromString(other));
        patchArticle(token, id, json.writeValueAsString(Map.of("slug", slug))).andExpect(status().isConflict());
        String newSlug = "patch-" + UUID.randomUUID();
        patchArticle(token, id, json.writeValueAsString(Map.of("slug", newSlug))).andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM post_slug_routes WHERE post_id=?", Integer.class, UUID.fromString(id)));
        patchArticle(token, UUID.randomUUID().toString(), "{\"title\":\"Missing\"}")
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", UUID.fromString(id));
        patchArticle(token, id, "{\"title\":\"Deleted\"}").andExpect(status().isNotFound());
    }

    @Test void patchRequiresOwnershipAndEditorToPublish() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        patchArticle(null, id, "{\"title\":\"No token\"}").andExpect(status().isUnauthorized());
        patchArticle(token, id, "{\"status\":\"PUBLISHED\"}").andExpect(status().isForbidden());
        email = UUID.randomUUID() + "@example.com";
        create();
        String otherToken = token();
        patchArticle(otherToken, id, "{\"title\":\"Other author\"}").andExpect(status().isForbidden());
        grant("EDITOR");
        patchArticle(otherToken, id, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publishedAt").value(NOW.toString()));
        patchArticle(token, id, "{\"title\":\"Published edit\"}").andExpect(status().isForbidden());
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        patchArticle(otherToken, id, "{\"title\":\"Editor update\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publishedAt").value(NOW.toString()));
        patchArticle(otherToken, id, "{\"status\":\"ARCHIVED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publishedAt").value(NOW.toString()));
        patchArticle(otherToken, id, "{\"status\":\"PUBLISHED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedAt").value(NOW.plusSeconds(60).toString()));
    }

    ResultActions detailArticle(String token, String id) throws Exception {
        var request = get("/api/blog/posts/" + articleSlug(id));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    @Test void detailReturnsFullPostForEveryStatusAndReflectsPatch() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        for (String state : new String[]{"DRAFT", "PUBLISHED", "ARCHIVED"}) {
            jdbc.update("UPDATE posts SET status=?,published_at=? WHERE id=?",
                    state, java.sql.Timestamp.from(NOW), UUID.fromString(id));
            detailArticle(token, id).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.title").value("Budgeting"))
                    .andExpect(jsonPath("$.data.status").value(state))
                    .andExpect(jsonPath("$.data.contentHtml").isNotEmpty())
                    .andExpect(jsonPath("$.data.categoryIds.length()").value(1))
                    .andExpect(jsonPath("$.data.tagIds.length()").value(1))
                    .andExpect(jsonPath("$.data.authorId").isNotEmpty())
                    .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        }
        grant("EDITOR");
        patchArticle(token, id, "{\"title\":\"After edit\",\"tagIds\":[]}").andExpect(status().isOk());
        detailArticle(token, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("After edit"))
                .andExpect(jsonPath("$.data.tagIds.length()").value(0));
    }

    @Test void detailEnforcesCmsAuthenticationOwnershipAndMissingPosts() throws Exception {
        create();
        String ownerToken = token();
        String id = draftId(ownerToken);
        detailArticle(null, id).andExpect(status().isUnauthorized());
        detailArticle("invalid-token", id).andExpect(status().isUnauthorized());
        detailArticle(ownerToken, "missing-slug").andExpect(status().isNotFound());
        detailArticle(ownerToken, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        email = UUID.randomUUID() + "@example.com";
        create();
        String otherToken = token();
        detailArticle(otherToken, id).andExpect(status().isForbidden());
        grant("EDITOR");
        detailArticle(otherToken, id).andExpect(status().isOk());
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        grant("ADMIN");
        detailArticle(otherToken, id).andExpect(status().isOk());
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        detailArticle(otherToken, id).andExpect(status().isForbidden());
        jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", UUID.fromString(id));
        detailArticle(ownerToken, id).andExpect(status().isNotFound());
    }

    ResultActions deleteArticle(String token, String id) throws Exception {
        var request = delete("/api/blog/posts/" + articleSlug(id));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    @Test void softDeleteRetainsDataAndHidesPostFromCms() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        UUID uuid = UUID.fromString(id);
        String html = jdbc.queryForObject("SELECT content_html FROM posts WHERE id=?", String.class, uuid);
        jdbc.update("UPDATE posts SET scheduled_at=? WHERE id=?", java.sql.Timestamp.from(NOW.plusSeconds(3600)), uuid);
        long total = jdbc.queryForObject("SELECT count(*) FROM posts WHERE deleted_at IS NULL", Long.class);
        deleteArticle(token, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("Blog post deleted successfully"));
        assertEquals(NOW, jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id=?", java.sql.Timestamp.class, uuid).toInstant());
        assertEquals(html, jdbc.queryForObject("SELECT content_html FROM posts WHERE id=?", String.class, uuid));
        assertNull(jdbc.queryForObject("SELECT scheduled_at FROM posts WHERE id=?", java.sql.Timestamp.class, uuid));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_categories WHERE post_id=?", Integer.class, uuid));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_tags WHERE post_id=?", Integer.class, uuid));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM post_slug_routes WHERE post_id=?", Integer.class, uuid));
        mvc.perform(get("/api/blog/posts").header("Authorization", "Bearer " + token).param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paging.totalItem").value(total - 1))
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        detailArticle(token, id).andExpect(status().isNotFound());
        patchArticle(token, id, "{\"title\":\"Deleted\"}").andExpect(status().isNotFound());
        deleteArticle(token, id).andExpect(status().isNotFound());
    }

    @Test void softDeleteRejectsInvalidSessionsRolesAndOtherAuthors() throws Exception {
        create();
        String ownerToken = token();
        String id = draftId(ownerToken);
        deleteArticle(null, id).andExpect(status().isUnauthorized());
        deleteArticle("invalid-token", id).andExpect(status().isUnauthorized());
        deleteArticle(ownerToken, "invalid-id").andExpect(status().isNotFound());
        deleteArticle(ownerToken, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        email = UUID.randomUUID() + "@example.com";
        create();
        String otherToken = token();
        deleteArticle(otherToken, id).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?)", email);
        deleteArticle(otherToken, id).andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id=?", java.sql.Timestamp.class, UUID.fromString(id)));
        detailArticle(ownerToken, id).andExpect(status().isOk());
    }

    @Test void softDeleteRequiresEditorOrAdminForPublishedAndArchivedPosts() throws Exception {
        create();
        String token = token();
        for (String role : new String[]{"EDITOR", "ADMIN"}) {
            for (String state : new String[]{"DRAFT", "PUBLISHED", "ARCHIVED"}) {
                jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email=?) "
                        + "AND role_id IN (SELECT id FROM roles WHERE name IN ('ADMIN','EDITOR'))", email);
                String id = draftId(token);
                jdbc.update("UPDATE posts SET status=?,published_at=? WHERE id=?", state,
                        java.sql.Timestamp.from(NOW), UUID.fromString(id));
                if (!state.equals("DRAFT")) {
                    deleteArticle(token, id).andExpect(status().isForbidden());
                    assertNull(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id=?", java.sql.Timestamp.class, UUID.fromString(id)));
                }
                grant(role);
                deleteArticle(token, id).andExpect(status().isOk());
            }
        }
    }

    @Test void publicArticlesOnlyExposeVisiblePublishedPostsWithoutCmsFields() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        UUID uuid = UUID.fromString(id);
        String slug = jdbc.queryForObject("SELECT slug FROM posts WHERE id=?", String.class, uuid);
        String path = "/api/blog/published/" + slug;
        mvc.perform(get(path)).andExpect(status().isNotFound());
        jdbc.update("UPDATE posts SET status='PUBLISHED',published_at=? WHERE id=?",
                java.sql.Timestamp.from(NOW.plusSeconds(60)), uuid);
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get("/api/blog/published").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        jdbc.update("UPDATE posts SET published_at=? WHERE id=?", java.sql.Timestamp.from(NOW), uuid);
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.slug").value(slug))
                .andExpect(jsonPath("$.data.contentHtml").isNotEmpty())
                .andExpect(jsonPath("$.data.categories[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.categories[0].slug").isNotEmpty())
                .andExpect(jsonPath("$.data.tags[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.authorId").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());
        long total = jdbc.queryForObject("SELECT count(*) FROM posts WHERE status='PUBLISHED' AND deleted_at IS NULL AND published_at<=?",
                Long.class, java.sql.Timestamp.from(NOW));
        mvc.perform(get("/api/blog/published").param("limit", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paging.totalItem").value(total))
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.hasItem(id)))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].authorName",
                        org.hamcrest.Matchers.contains("CMS Author")))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].categories[*].id",
                        org.hamcrest.Matchers.hasItem(jdbc.queryForObject(
                                "SELECT category_id::text FROM post_categories WHERE post_id=?", String.class, uuid))))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].categories[*].name",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.isEmptyOrNullString()))))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].categories[*].slug",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.isEmptyOrNullString()))))
                .andExpect(jsonPath("$.data[0].contentHtml").doesNotExist())
                .andExpect(jsonPath("$.data[0].authorId").doesNotExist());
        mvc.perform(get("/api/blog/published").param("skip", String.valueOf(total)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.paging.hasMore").value(false));
        jdbc.update("UPDATE posts SET status='ARCHIVED' WHERE id=?", uuid);
        mvc.perform(get(path)).andExpect(status().isNotFound());
        jdbc.update("UPDATE posts SET status='PUBLISHED',deleted_at=CURRENT_TIMESTAMP WHERE id=?", uuid);
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get("/api/blog/published").param("limit", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paging.totalItem").value(total - 1))
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        mvc.perform(get("/api/blog/published/missing-article")).andExpect(status().isNotFound());
    }

    @Test void publicArticleListAndDetailIncludeHeroImage() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        UUID uuid = UUID.fromString(id);
        String slug = articleSlug(id);
        jdbc.update("UPDATE posts SET status='PUBLISHED',published_at=? WHERE id=?",
                java.sql.Timestamp.from(NOW), uuid);
        for (String path : new String[]{"/api/blog/published/" + slug, "/api/blog/published?limit=100"}) {
            String field = path.contains("?") ? "$.data[?(@.id == '" + id + "')].heroImage" : "$.data.heroImage";
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath(field).value(path.contains("?")
                            ? org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())
                            : org.hamcrest.Matchers.nullValue()));
        }
        UUID mediaId = UUID.randomUUID();
        String hero = "https://example.com/blog/hero.jpg";
        jdbc.update("""
                INSERT INTO media(id,uploaded_by,url,mime_type,file_size_bytes,storage_provider)
                SELECT ?,author_id,?,'image/jpeg',123,'test' FROM posts WHERE id=?
                """, mediaId, hero, uuid);
        jdbc.update("UPDATE posts SET featured_media_id=? WHERE id=?", mediaId, uuid);
        mvc.perform(get("/api/blog/published/" + slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heroImage").value(hero));
        mvc.perform(get("/api/blog/published").param("limit", "100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].heroImage",
                        org.hamcrest.Matchers.contains(hero)));
    }

    @Test void publicDetailIncludesAuthorProfileAndSocialLinks() throws Exception {
        create();
        String token = token();
        String id = draftId(token);
        UUID uuid = UUID.fromString(id);
        String path = "/api/blog/published/" + articleSlug(id);
        jdbc.update("UPDATE posts SET status='PUBLISHED',published_at=? WHERE id=?",
                java.sql.Timestamp.from(NOW), uuid);
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.name").value("CMS Author"))
                .andExpect(jsonPath("$.data.author.photoProfile").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.author.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.author.socialMedia").isEmpty());
        UUID authorId = jdbc.queryForObject("SELECT author_id FROM posts WHERE id=?", UUID.class, uuid);
        jdbc.update("UPDATE users SET photo_profile=?,description=? WHERE id=?",
                "https://example.com/avatar.jpg", "Penulis tentang keuangan pribadi.", authorId);
        jdbc.update("INSERT INTO author_social_links(author_id,platform,url) VALUES (?,?,?),(?,?,?)",
                authorId, "instagram", "https://instagram.com/author",
                authorId, "linkedin", "https://linkedin.com/in/author");
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.name").value("CMS Author"))
                .andExpect(jsonPath("$.data.author.photoProfile").value("https://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.data.author.description").value("Penulis tentang keuangan pribadi."))
                .andExpect(jsonPath("$.data.author.socialMedia.instagram").value("https://instagram.com/author"))
                .andExpect(jsonPath("$.data.author.socialMedia.linkedin").value("https://linkedin.com/in/author"))
                .andExpect(jsonPath("$.data.author.email").doesNotExist())
                .andExpect(jsonPath("$.data.author.passwordHash").doesNotExist());
    }

    UUID uploadedMedia() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO media(id,uploaded_by,url,mime_type,file_size_bytes,storage_provider,provider_asset_id,unused_since)
                SELECT ?,id,?,'image/png',1,'imagekit',?,? FROM users WHERE email=?
                """, id, "https://ik.imagekit.io/test/" + id + ".png", id.toString(),
                java.sql.Timestamp.from(NOW), email);
        return id;
    }

    String mediaUrl(UUID id) { return "https://ik.imagekit.io/test/" + id + ".png"; }

    String createArticleWithMedia(String token, UUID content, UUID hero) throws Exception {
        var body = postBody(token);
        body.put("contentHtml", "<p>Article</p>" + (content == null ? "" :
                "<img src='" + mediaUrl(content) + "?tr=w-800&amp;x=1'><img src='" + mediaUrl(content) + "'>"));
        if (hero != null) body.put("featuredMediaId", hero);
        var response = postArticle(token, body).andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("data").get("id").asString();
    }

    @Test void mediaUsageTracksContentHeroSharedPostsAndReleaseTime() throws Exception {
        create();
        String token = token();
        UUID image = uploadedMedia();
        UUID replacement = uploadedMedia();
        String first = createArticleWithMedia(token, image, image);
        String second = createArticleWithMedia(token, image, null);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM post_media WHERE media_id=?", Integer.class, image));
        assertNull(jdbc.queryForObject("SELECT unused_since FROM media WHERE id=?", java.sql.Timestamp.class, image));
        patchArticle(token, first, "{\"contentHtml\":\"<p>No inline image</p>\"}").andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM post_media WHERE media_id=?", Integer.class, image));
        patchArticle(token, first, json.writeValueAsString(Map.of("featuredMediaId", replacement)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.featuredMediaId").value(replacement.toString()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_media WHERE media_id=?", Integer.class, image));
        deleteArticle(token, second).andExpect(status().isOk());
        assertEquals(java.sql.Timestamp.from(NOW),
                jdbc.queryForObject("SELECT unused_since FROM media WHERE id=?", java.sql.Timestamp.class, image));
        patchArticle(token, first, "{\"featuredMediaId\":null}").andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM post_media WHERE media_id=?", Integer.class, replacement));
        assertEquals(java.sql.Timestamp.from(NOW),
                jdbc.queryForObject("SELECT unused_since FROM media WHERE id=?", java.sql.Timestamp.class, replacement));
    }

    @Test void invalidMediaRollsBackPostAndUsageChanges() throws Exception {
        create();
        String token = token();
        UUID image = uploadedMedia();
        String id = createArticleWithMedia(token, image, image);
        patchArticle(token, id, json.writeValueAsString(Map.of("featuredMediaId", UUID.randomUUID(),
                "contentHtml", "<p>Changed</p>"))).andExpect(status().isBadRequest());
        assertEquals(image, jdbc.queryForObject("SELECT featured_media_id FROM posts WHERE id=?", UUID.class, UUID.fromString(id)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM post_media WHERE post_id=?", Integer.class, UUID.fromString(id)));
        UUID deleted = uploadedMedia();
        jdbc.update("UPDATE media SET lifecycle_status='DELETED',deleted_at=? WHERE id=?", java.sql.Timestamp.from(NOW), deleted);
        patchArticle(token, id, json.writeValueAsString(Map.of("contentHtml",
                "<p>Reused</p><img src='" + mediaUrl(deleted) + "'>"))).andExpect(status().isBadRequest());
        var body = postBody(token);
        body.put("featuredMediaId", deleted);
        postArticle(token, body).andExpect(status().isBadRequest());
    }

    @Test void mediaMigrationBackfillsExistingContentAndHeroWithoutKeepingDeletedPosts() {
        String schema = "media_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                BLOG.getJdbcUrl() + (BLOG.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                BLOG.getUsername(), BLOG.getPassword());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").target("8").load().migrate();
        var legacy = new JdbcTemplate(source);
        UUID author = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID inline = UUID.randomUUID();
        UUID hero = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        legacy.update("INSERT INTO users(id,display_name,email,password_hash) VALUES (?,'Author','media@example.com','hash')", author);
        for (UUID media : new UUID[]{inline, hero, orphan}) {
            legacy.update("""
                    INSERT INTO media(id,uploaded_by,url,mime_type,file_size_bytes,storage_provider,provider_asset_id)
                    VALUES (?, ?, ?, 'image/png',1,'imagekit',?)
                    """, media, author, "https://example.com/" + media, media.toString());
        }
        legacy.update("""
                INSERT INTO posts(id,author_id,title,slug,content_html,featured_media_id)
                VALUES (?,?,'Active','media-active',?,?)
                """, active, author, "<p>Content</p><img src='https://example.com/" + inline + "?tr=w-800'>", hero);
        legacy.update("""
                INSERT INTO posts(id,author_id,title,slug,content_html,deleted_at)
                VALUES (?,?,'Deleted','media-deleted',?,CURRENT_TIMESTAMP)
                """, deleted, author, "<img src='https://example.com/" + orphan + "'>");
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").load().migrate();
        assertEquals(2, legacy.queryForObject("SELECT count(*) FROM post_media WHERE post_id=?", Integer.class, active));
        assertEquals(0, legacy.queryForObject("SELECT count(*) FROM post_media WHERE post_id=?", Integer.class, deleted));
        assertNull(legacy.queryForObject("SELECT unused_since FROM media WHERE id=?", java.sql.Timestamp.class, inline));
        assertNotNull(legacy.queryForObject("SELECT unused_since FROM media WHERE id=?", java.sql.Timestamp.class, orphan));
    }

    @Test void publicArticlesValidatePaginationAndLeaveCmsAuthenticated() throws Exception {
        mvc.perform(get("/api/blog/published").param("skip", "-1")).andExpect(status().isBadRequest());
        for (String limit : new String[]{"0", "101", "invalid"}) {
            mvc.perform(get("/api/blog/published").param("limit", limit)).andExpect(status().isBadRequest());
        }
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/blog/posts")).andExpect(status().isUnauthorized());
        detailArticle(null, id).andExpect(status().isUnauthorized());
        patchArticle(null, id, "{\"title\":\"Unauthorized\"}").andExpect(status().isUnauthorized());
        deleteArticle(null, id).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/blog/tags")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/blog/published")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/blog/published/example")).andExpect(status().isMethodNotAllowed());
    }

    @Test void publicArticleCorsAllowsMainWebsiteReadOnly() throws Exception {
        mvc.perform(get("/api/blog/published").header("Origin", "https://www.myartoku.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.myartoku.com"));
        mvc.perform(get("/api/blog/published/missing-article").header("Origin", "https://www.myartoku.com"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.myartoku.com"));
        mvc.perform(options("/api/blog/published").header("Origin", "https://www.myartoku.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods", "GET,OPTIONS"));
        mvc.perform(options("/api/blog/published").header("Origin", "https://www.myartoku.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/blog/published").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/blog/posts").header("Origin", "https://www.myartoku.com"))
                .andExpect(status().isForbidden());
    }

    @Test void createsPostWithSameSlugAfterSoftDeleteAndStillRejectsActiveDuplicates() throws Exception {
        create();
        String token = token();
        var body = postBody(token);
        var created = postArticle(token, body).andExpect(status().isCreated()).andReturn();
        String oldId = json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asString();
        postArticle(token, body).andExpect(status().isConflict());
        deleteArticle(token, oldId).andExpect(status().isOk());
        var replacement = postArticle(token, body).andExpect(status().isCreated()).andReturn();
        String newId = json.readTree(replacement.getResponse().getContentAsString()).get("data").get("id").asString();
        assertNotEquals(oldId, newId);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM posts WHERE slug=?", Integer.class, body.get("slug")));
        assertEquals(UUID.fromString(newId), jdbc.queryForObject("SELECT post_id FROM post_slug_routes WHERE slug=?", UUID.class, body.get("slug")));
        mvc.perform(get("/api/blog/posts/" + oldId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        detailArticle(token, newId).andExpect(status().isOk());
        postArticle(token, body).andExpect(status().isConflict());
        grant("EDITOR");
        patchArticle(token, newId, "{\"status\":\"PUBLISHED\"}").andExpect(status().isOk());
        mvc.perform(get("/api/blog/published/" + body.get("slug"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(newId));
    }

    @Test void deletionReleasesHistoricalSlugsForCreateAndPatch() throws Exception {
        create();
        String token = token();
        var body = postBody(token);
        var created = postArticle(token, body).andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asString();
        String renamed = "renamed-" + UUID.randomUUID();
        patchArticle(token, id, json.writeValueAsString(Map.of("slug", renamed))).andExpect(status().isOk());
        postArticle(token, body).andExpect(status().isConflict());
        String otherId = draftId(token);
        patchArticle(token, otherId, json.writeValueAsString(Map.of("slug", renamed))).andExpect(status().isConflict());
        deleteArticle(token, id).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM post_slug_routes WHERE post_id=?", Integer.class, UUID.fromString(id)));
        postArticle(token, body).andExpect(status().isCreated());
        patchArticle(token, otherId, json.writeValueAsString(Map.of("slug", renamed))).andExpect(status().isOk());
        assertEquals(renamed, jdbc.queryForObject("SELECT slug FROM posts WHERE id=?", String.class, UUID.fromString(id)));
    }

    @Test void migrationReleasesSlugsOfPostsDeletedBeforeVersionSeven() {
        String schema = "slug_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                BLOG.getJdbcUrl() + (BLOG.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                BLOG.getUsername(), BLOG.getPassword());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").target("6").load().migrate();
        var legacy = new JdbcTemplate(source);
        UUID author = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        legacy.update("INSERT INTO users(id,display_name,email,password_hash) VALUES (?,'Author','legacy@example.com','hash')", author);
        legacy.update("INSERT INTO posts(id,author_id,title,slug) VALUES (?,?,'Legacy','legacy-original')", deleted, author);
        legacy.update("UPDATE posts SET slug='legacy-current',deleted_at=CURRENT_TIMESTAMP WHERE id=?", deleted);
        assertEquals(2, legacy.queryForObject("SELECT count(*) FROM post_slug_routes", Integer.class));
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/blog-migration").load().migrate();
        assertEquals(0, legacy.queryForObject("SELECT count(*) FROM post_slug_routes", Integer.class));
        for (String slug : new String[]{"legacy-original", "legacy-current"}) {
            legacy.update("INSERT INTO posts(id,author_id,title,slug) VALUES (?,?,'New',?)", UUID.randomUUID(), author, slug);
        }
        assertEquals(3, legacy.queryForObject("SELECT count(*) FROM posts", Integer.class));
        assertNotNull(legacy.queryForObject("SELECT deleted_at FROM posts WHERE id=?", java.sql.Timestamp.class, deleted));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                legacy.update("UPDATE posts SET deleted_at=NULL WHERE id=?", deleted));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"https://cms.myartoku.com", "http://localhost:30001"})
    void cmsCorsAllowsDashboardOriginsAndStillRequiresToken(String origin) throws Exception {
        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            mvc.perform(options("/api/blog/posts/" + UUID.randomUUID())
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", method)
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
        mvc.perform(get("/api/blog/posts").header("Origin", origin))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options("/api/blog/auth/login").header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

}
