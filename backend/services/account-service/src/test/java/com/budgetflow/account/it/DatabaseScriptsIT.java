package com.budgetflow.account.it;

import com.budgetflow.common.test.DatabaseScripts;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseScriptsIT {

    @Test
    void schemaAndResetRebuildAccountDb() throws Exception {
        DatabaseScripts.assertRebuildable(List.of(
                "account", "balance_entry", "operation_tombstone", "outbox_message", "inbox_message"));
    }

    @Test
    void migrationsBuildExactlyTheSchemaInSchemaSql() throws Exception {
        com.budgetflow.common.test.DatabaseScripts.assertMigrationsMatchSchema();
    }

}
