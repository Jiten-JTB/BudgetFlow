package com.budgetflow.budget.it;

import com.budgetflow.common.test.DatabaseScripts;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseScriptsIT {

    @Test
    void schemaAndResetRebuildBudgetDb() throws Exception {
        DatabaseScripts.assertRebuildable(List.of(
                "budget", "budget_period_usage", "budget_consumption", "budget_operation", "outbox_message", "inbox_message"));
    }
}
