package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class FlywayBaselineIT extends IdentityIntegrationTest {

    @Autowired JdbcClient jdbc;

    @Test
    void schemaSqlIsAppliedAsFlywayBaselineV1() {
        var row = jdbc.sql("SELECT version, script, success FROM flyway_schema_history WHERE version = '1'")
                .query().singleRow();

        assertThat(row.get("script")).isEqualTo("V1__baseline.sql");
        assertThat(row.get("success")).isEqualTo(true);
    }
}
