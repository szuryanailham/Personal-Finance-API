package com.ilham.personal_finance_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

public class GuidanceMigrationTest extends AbstractIntegrationTest {
    @Test
    void guidanceDefaultsApplyToExistingAndNewUsers() throws SQLException {
        String schema = "guidance_migration_test";
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).locations("classpath:db/migration").cleanDisabled(false);
        Flyway legacy = configuration.target("7").load();
        legacy.clean();
        legacy.migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO users (id, first_name, last_name, email, password)
                    VALUES (gen_random_uuid(), 'Legacy', 'User', 'guidance-legacy@example.com', 'hash')
                    """);
                Flyway.configure()
                        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                        .schemas(schema).locations("classpath:db/migration").load().migrate();
                try (var rows = statement.executeQuery("SELECT * FROM users")) {
                    int count = 0;
                    while (rows.next()) {
                        assertFalse(rows.getBoolean("is_guidance_completed"));
                        count++;
                    }
                    assertEquals(4, count, "three seeded users and the legacy user survive");
                }
                try (var rows = statement.executeQuery(
                        "SELECT first_name, password FROM users WHERE email = 'guidance-legacy@example.com'")) {
                    rows.next();
                    assertEquals("Legacy", rows.getString("first_name"));
                    assertEquals("hash", rows.getString("password"));
                }
                statement.executeUpdate("""
                    INSERT INTO users (id, first_name, last_name, email, password)
                    VALUES (gen_random_uuid(), 'New', 'User', 'guidance-new@example.com', 'hash')
                    """);
                try (var rows = statement.executeQuery(
                        "SELECT is_guidance_completed FROM users WHERE email = 'guidance-new@example.com'")) {
                    rows.next();
                    assertFalse(rows.getBoolean(1));
                }
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE users SET is_guidance_completed = NULL WHERE email = 'guidance-new@example.com'"));
            }
        }
    }
}
