package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** db/schema.sql and db/reset.sql must be able to rebuild identity_db from nothing, repeatedly. */
class DatabaseScriptsIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final List<String> EXPECTED_TABLES = List.of(
            "auth_audit_event", "email_outbox", "email_verification_token", "inbox_message", "oauth2_authorization", "oauth2_authorization_consent",
            "oauth2_registered_client", "outbox_message", "users");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void schemaCreatesAllTablesAndResetDropsEverythingIncludingFlywayHistory() throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(Path.of("db/schema.sql")));
            assertThat(tables(st)).containsExactlyElementsOf(EXPECTED_TABLES);

            st.execute("CREATE TABLE flyway_schema_history (id int)");
            st.execute(Files.readString(Path.of("db/reset.sql")));
            assertThat(tables(st)).isEmpty();

            st.execute(Files.readString(Path.of("db/schema.sql")));
            assertThat(tables(st)).as("rebuild after reset").containsExactlyElementsOf(EXPECTED_TABLES);
        }
    }

    @Test
    void resetIsSafeOnEmptyDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute(Files.readString(Path.of("db/reset.sql")));
            st.execute(Files.readString(Path.of("db/reset.sql")));
            assertThat(tables(st)).isEmpty();
        }
    }

    private static List<String> tables(Statement st) throws Exception {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    @Test
    void migrationsBuildExactlyTheSchemaInSchemaSql() throws Exception {
        com.budgetflow.common.test.DatabaseScripts.assertMigrationsMatchSchema();
    }

}
