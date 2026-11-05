package com.budgetflow.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the per-database contract: {@code db/schema.sql} builds the schema from nothing,
 * {@code db/reset.sql} removes all of it (incl. Flyway history), and the pair can rebuild repeatedly.
 */
public final class DatabaseScripts {

    private DatabaseScripts() {}

    public static void assertRebuildable(List<String> expectedTables) throws Exception {
        String schema = Files.readString(Path.of("db/schema.sql"));
        String reset = Files.readString(Path.of("db/reset.sql"));
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(ServiceContainers.POSTGRES_IMAGE)) {
            postgres.start();
            try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement st = c.createStatement()) {
                st.execute(reset);
                assertThat(objects(st)).as("reset.sql is safe on an empty database").isEmpty();

                st.execute(schema);
                assertThat(tables(st)).as("tables created by schema.sql").containsExactlyInAnyOrderElementsOf(expectedTables);

                st.execute("CREATE TABLE flyway_schema_history (id int)");
                st.execute(reset);
                assertThat(objects(st)).as("reset.sql drops every table, view, sequence and type").isEmpty();

                st.execute(schema);
                assertThat(tables(st)).as("rebuild after reset").containsExactlyInAnyOrderElementsOf(expectedTables);
            }
        }
    }

    private static List<String> tables(Statement st) throws Exception {
        return list(st, "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
    }

    private static List<String> objects(Statement st) throws Exception {
        return list(st, """
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'v', 'm', 'S', 'p')
                UNION ALL
                SELECT t.typname FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = 'public' AND t.typtype IN ('e', 'd')""");
    }

    private static List<String> list(Statement st, String sql) throws Exception {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
