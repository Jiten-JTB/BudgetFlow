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

    /**
     * Post-production contract: applying {@code db/migrations/V*.sql} in version order must produce exactly the schema that
     * {@code db/schema.sql} (the source of truth) creates: same columns, types, defaults, constraints and indexes.
     */
    public static void assertMigrationsMatchSchema() throws Exception {
        String schema = Files.readString(Path.of("db/schema.sql"));
        List<Path> migrations;
        try (var files = Files.list(Path.of("db/migrations"))) {
            migrations = files.filter(f -> f.getFileName().toString().matches("V\\d+__.+\\.sql"))
                    .sorted(java.util.Comparator.comparingInt(DatabaseScripts::version)).toList();
        }
        assertThat(migrations).as("db/migrations starts at V1__baseline.sql").isNotEmpty();
        assertThat(migrations.getFirst().getFileName().toString()).isEqualTo("V1__baseline.sql");
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(ServiceContainers.POSTGRES_IMAGE)) {
            postgres.start();
            try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement st = c.createStatement()) {
                st.execute("CREATE SCHEMA from_schema; CREATE SCHEMA from_migrations");
                st.execute("SET search_path TO from_schema");
                st.execute(schema);
                st.execute("SET search_path TO from_migrations");
                for (Path m : migrations) {
                    st.execute(Files.readString(m));
                }
                st.execute("SET search_path TO public");
                assertThat(structure(st, "from_migrations")).as("schema built by db/migrations")
                        .containsExactlyElementsOf(structure(st, "from_schema"));
            }
        }
    }

    private static int version(Path migration) {
        String name = migration.getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }

    /** A schema-independent description of every table: columns, constraints and indexes, sorted. */
    private static List<String> structure(Statement st, String schemaName) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.addAll(list(st, """
                SELECT 'column ' || table_name || '.' || column_name || ' ' || data_type
                       || coalesce('(' || character_maximum_length || ')', '')
                       || coalesce('(' || numeric_precision || ',' || numeric_scale || ')', '')
                       || ' null=' || is_nullable || ' default=' || coalesce(column_default, '')
                FROM information_schema.columns WHERE table_schema = '""" + schemaName + "'"));
        lines.addAll(list(st, """
                SELECT 'constraint ' || cl.relname || '.' || co.conname || ' ' || pg_get_constraintdef(co.oid)
                FROM pg_constraint co JOIN pg_class cl ON cl.oid = co.conrelid
                JOIN pg_namespace n ON n.oid = cl.relnamespace WHERE n.nspname = '""" + schemaName + "'"));
        lines.addAll(list(st, "SELECT 'index ' || tablename || '.' || indexname || ' ' || replace(indexdef, '"
                + schemaName + ".', '') FROM pg_indexes WHERE schemaname = '" + schemaName + "'"));
        // Foreign keys and indexes name their schema; drop it so the two copies compare by structure alone.
        List<String> normalised = new ArrayList<>(lines.stream().map(l -> l.replace(schemaName + ".", "")).toList());
        java.util.Collections.sort(normalised);
        return normalised;
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
