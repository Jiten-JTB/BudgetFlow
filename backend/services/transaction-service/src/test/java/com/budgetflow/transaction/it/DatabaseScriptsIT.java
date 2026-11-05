package com.budgetflow.transaction.it;

import com.budgetflow.common.test.DatabaseScripts;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseScriptsIT {

    @Test
    void schemaAndResetRebuildTransactionDb() throws Exception {
        DatabaseScripts.assertRebuildable(List.of(
                "category", "income_source", "money_transaction", "outbox_message", "inbox_message"));
    }
}
