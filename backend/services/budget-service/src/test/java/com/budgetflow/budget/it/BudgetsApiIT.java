package com.budgetflow.budget.it;

import static com.budgetflow.common.test.ApiTokens.user;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BudgetsApiIT extends BudgetIntegrationTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();

    @Test
    void createsBudgetAndReportsZeroUsageForCurrentPeriod() throws Exception {
        String id = id(create(alice, budgetJson("Food", food, "MONTHLY", "12000", "2026-11-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limit").value("12000.0000"))
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.categoryId").value(food.toString())));

        mvc.perform(get("/api/v1/budgets/{id}/status", id).param("date", "2026-11-15").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-11-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-30"))
                .andExpect(jsonPath("$.spent").value("0.0000"))
                .andExpect(jsonPath("$.remaining").value("12000.0000"))
                .andExpect(jsonPath("$.percentUsed").value(0))
                .andExpect(jsonPath("$.state").value("ON_TRACK"));
    }

    @Test
    void oneActiveBudgetPerScopePeriodAndCurrency() throws Exception {
        create(alice, budgetJson("Food", food, "MONTHLY", "100", "2026-11-01")).andExpect(status().isCreated());
        create(alice, budgetJson("Food again", food, "MONTHLY", "200", "2026-11-01"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_BUDGET"));
        create(alice, budgetJson("Food weekly", food, "WEEKLY", "50", "2026-11-02")).andExpect(status().isCreated());
        create(alice, budgetJson("Everything", null, "MONTHLY", "50000", "2026-11-01")).andExpect(status().isCreated());
        create(alice, budgetJson("Everything 2", null, "MONTHLY", "1", "2026-11-01")).andExpect(status().isConflict());
    }

    @Test
    void updatesArchivesAndListsWithStatus() throws Exception {
        String id = id(create(alice, budgetJson("Food", food, "MONTHLY", "100", "2026-11-01")));

        mvc.perform(patch("/api/v1/budgets/{id}", id).with(user(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":\"250.5\",\"alertThresholdPercent\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value("250.5000"))
                .andExpect(jsonPath("$.alertThresholdPercent").value(90));
        mvc.perform(get("/api/v1/budgets/status").param("date", "2026-11-10").with(user(alice)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].budget.id").value(id));
        mvc.perform(post("/api/v1/budgets/{id}/archive", id).with(user(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mvc.perform(get("/api/v1/budgets").with(user(alice))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/budgets").param("includeArchived", "true").with(user(alice)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void validatesInput() throws Exception {
        create(alice, budgetJson("Food", food, "DAILY", "100", "2026-11-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        create(alice, budgetJson("Food", food, "MONTHLY", "0", "2026-11-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
        create(alice, budgetJson("Food", food, "MONTHLY", "abc", "2026-11-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        create(alice, budgetJson("Food", food, "MONTHLY", "10", "1/11/2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE"));
    }

    @Test
    void budgetsAreIsolatedPerUser() throws Exception {
        String id = id(create(alice, budgetJson("Food", food, "MONTHLY", "100", "2026-11-01")));
        UUID bob = UUID.randomUUID();

        mvc.perform(get("/api/v1/budgets/{id}/status", id).with(user(bob))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUDGET_NOT_FOUND"));
        mvc.perform(post("/api/v1/budgets/{id}/archive", id).with(user(bob))).andExpect(status().isNotFound());
    }
}
