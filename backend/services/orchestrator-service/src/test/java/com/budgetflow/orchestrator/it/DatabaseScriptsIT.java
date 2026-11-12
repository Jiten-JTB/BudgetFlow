package com.budgetflow.orchestrator.it;

import com.budgetflow.common.test.DatabaseScripts;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseScriptsIT {

    @Test
    void schemaAndResetRebuildOrchestratorDb() throws Exception {
        DatabaseScripts.assertRebuildable(List.of(
                "saga_instance", "saga_event", "outbox_message", "inbox_message"));
    }

    @Test
    void migrationsBuildExactlyTheSchemaInSchemaSql() throws Exception {
        com.budgetflow.common.test.DatabaseScripts.assertMigrationsMatchSchema();
    }

}
