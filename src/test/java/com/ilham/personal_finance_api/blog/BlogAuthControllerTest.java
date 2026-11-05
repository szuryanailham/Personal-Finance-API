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

    @Test void registersOnlyInBlogAndKeepsFinanceFlyway() throws Exception {
        create();
        assertTrue(financeUsers.findByEmail(email).isEmpty());
        assertEquals("9", financeFlyway.info().current().getVersion().toString());
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertNotEquals(PASSWORD, stored);
        assertTrue(BCrypt.checkpw(PASSWORD, stored));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='post_daily_stats'", Integer.class));
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

}
