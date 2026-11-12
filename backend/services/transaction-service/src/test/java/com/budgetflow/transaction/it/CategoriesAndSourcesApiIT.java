package com.budgetflow.transaction.it;

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

class CategoriesAndSourcesApiIT extends TransactionIntegrationTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void createsTwoLevelCategoriesAndFiltersByKind() throws Exception {
        String food = createCategory(alice, "Food", "EXPENSE");
        postJson(alice, "/api/v1/categories", "{\"name\":\"Restaurants\",\"kind\":\"EXPENSE\",\"parentId\":\"" + food
                + "\",\"icon\":\"utensils\",\"color\":\"#ff0000\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(food))
                .andExpect(jsonPath("$.color").value("#FF0000"));
        createCategory(alice, "Salary", "INCOME");

        mvc.perform(get("/api/v1/categories").param("kind", "EXPENSE").with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void rejectsInvalidParents() throws Exception {
        String food = createCategory(alice, "Food", "EXPENSE");
        String child = id(postJson(alice, "/api/v1/categories", "{\"name\":\"Dining\",\"kind\":\"EXPENSE\",\"parentId\":\"" + food + "\"}"));
        String salary = createCategory(alice, "Salary", "INCOME");
        String bobs = createCategory(bob, "Bob Food", "EXPENSE");

        for (String parent : new String[] {child, salary}) {
            postJson(alice, "/api/v1/categories", "{\"name\":\"X\",\"kind\":\"EXPENSE\",\"parentId\":\"" + parent + "\"}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARENT_CATEGORY"));
        }
        postJson(alice, "/api/v1/categories", "{\"name\":\"X\",\"kind\":\"EXPENSE\",\"parentId\":\"" + bobs + "\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void siblingNamesAreUniqueUntilArchived() throws Exception {
        String food = createCategory(alice, "Food", "EXPENSE");
        postJson(alice, "/api/v1/categories", "{\"name\":\"FOOD\",\"kind\":\"EXPENSE\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
        postJson(alice, "/api/v1/categories", "{\"name\":\"Food\",\"kind\":\"INCOME\"}").andExpect(status().isCreated());

        mvc.perform(post("/api/v1/categories/{id}/archive", food).with(user(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        postJson(alice, "/api/v1/categories", "{\"name\":\"Food\",\"kind\":\"EXPENSE\"}").andExpect(status().isCreated());
    }

    @Test
    void categoriesAreIsolatedPerUser() throws Exception {
        String food = createCategory(alice, "Food", "EXPENSE");

        mvc.perform(patch("/api/v1/categories/{id}", food).with(user(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/categories").with(user(bob))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void managesIncomeSources() throws Exception {
        UUID account = UUID.randomUUID();
        String acme = id(postJson(alice, "/api/v1/income-sources",
                "{\"name\":\"Acme Corp\",\"type\":\"SALARY\",\"defaultAccountId\":\"" + account + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SALARY"))
                .andExpect(jsonPath("$.defaultAccountId").value(account.toString())));

        mvc.perform(patch("/api/v1/income-sources/{id}", acme).with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme Inc\",\"type\":\"BUSINESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme Inc"))
                .andExpect(jsonPath("$.type").value("BUSINESS"));
        postJson(alice, "/api/v1/income-sources", "{\"name\":\"acme inc\",\"type\":\"OTHER\"}")
                .andExpect(status().isConflict());
        postJson(alice, "/api/v1/income-sources", "{\"name\":\"Lottery\",\"type\":\"LUCK\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mvc.perform(post("/api/v1/income-sources/{id}/archive", acme).with(user(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/income-sources").with(user(alice))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/income-sources").param("includeArchived", "true").with(user(alice)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void acceptsPocketMoneyAsASourceType() throws Exception {
        postJson(alice, "/api/v1/income-sources", "{\"name\":\"Mom\",\"type\":\"POCKET_MONEY\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("POCKET_MONEY"));
    }
}
