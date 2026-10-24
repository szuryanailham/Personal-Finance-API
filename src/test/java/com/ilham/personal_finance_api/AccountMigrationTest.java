package com.ilham.personal_finance_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// V6 dijalankan di atas database yang sudah berisi user & transaksi lama (state V5)
public class AccountMigrationTest extends AbstractIntegrationTest {

    private static final String SCHEMA = "account_migration_test";
    private static final UUID LEGACY_USER_ID = UUID.randomUUID();
    private static final UUID LEGACY_CATEGORY_ID = UUID.randomUUID();

    private long transactionsBeforeV6;

    @BeforeEach
    void migrateLegacyDatabase() throws SQLException {
        Flyway v5 = flyway("5");
        v5.clean();
        v5.migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (id, first_name, last_name, email, password)
                VALUES ('%s', 'Legacy', 'User', 'legacy@example.com', 'hash')
                """.formatted(LEGACY_USER_ID));
            statement.executeUpdate("""
                INSERT INTO categories (id, name, type, user_id)
                VALUES ('%s', 'Gaji', 'INCOME', '%s')
                """.formatted(LEGACY_CATEGORY_ID, LEGACY_USER_ID));
            for (int i = 0; i < 3; i++) {
                statement.executeUpdate("""
                    INSERT INTO transactions (id, transaction_code, transaction_name, user_id, category_id, amount, transaction_date, is_deleted)
                    VALUES ('%s', 'TRX-LEGACY-%d', 'Gaji', '%s', '%s', 1000000, now(), %s)
                    """.formatted(UUID.randomUUID(), i, LEGACY_USER_ID, LEGACY_CATEGORY_ID, i == 2));
            }
            transactionsBeforeV6 = count(statement, "SELECT COUNT(*) FROM transactions");
        }

        flyway(null).migrate();
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(SCHEMA)
            .locations("classpath:db/migration")
            .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(SCHEMA);
        return connection;
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    @Test
    void testExistingUsersAndTransactionsSurvive() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertEquals(transactionsBeforeV6, count(statement, "SELECT COUNT(*) FROM transactions"));
            assertEquals(4, count(statement, "SELECT COUNT(*) FROM users"), "3 seeded users + 1 legacy user");
            assertEquals(0, count(statement, "SELECT COUNT(*) FROM users WHERE financial_setup_completed_at IS NOT NULL"),
                "existing users can still run the initial setup");
        }
    }

    @Test
    void testEveryUserGetsZeroMainAndSavingsAccounts() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            long users = count(statement, "SELECT COUNT(*) FROM users");
            assertEquals(users * 2, count(statement, "SELECT COUNT(*) FROM accounts"));
            assertEquals(users, count(statement, "SELECT COUNT(*) FROM accounts WHERE account_type = 'MAIN'"));
            assertEquals(users, count(statement, "SELECT COUNT(*) FROM accounts WHERE account_type = 'SAVINGS'"));
            assertEquals(0, count(statement, "SELECT COUNT(*) FROM accounts WHERE opening_balance <> 0"));
        }
    }

    @Test
    void testConstraintsRejectInvalidData() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                INSERT INTO accounts (id, user_id, name, account_type) VALUES (gen_random_uuid(), '%s', 'Dup', 'MAIN')
                """.formatted(LEGACY_USER_ID)), "duplicate MAIN account");
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                UPDATE accounts SET opening_balance = -1 WHERE user_id = '%s'
                """.formatted(LEGACY_USER_ID)), "negative opening balance");
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                UPDATE accounts SET account_type = 'WALLET' WHERE user_id = '%s'
                """.formatted(LEGACY_USER_ID)), "unknown account type");
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                INSERT INTO transfers (id, user_id, from_account_id, to_account_id, amount, created_at)
                SELECT gen_random_uuid(), user_id, id, id, 100, now() FROM accounts WHERE user_id = '%s' AND account_type = 'MAIN'
                """.formatted(LEGACY_USER_ID)), "transfer to the same account");
        }
    }
}
