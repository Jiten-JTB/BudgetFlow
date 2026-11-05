package com.budgetflow.account.it;

import static com.budgetflow.common.test.ApiTokens.user;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AccountsApiIT extends AccountIntegrationTest {

    @Autowired MockMvc mvc;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void opensAccountWithOpeningBalanceRecordedInLedger() throws Exception {
        String id = create(alice, "{\"name\":\"HDFC Savings\",\"type\":\"SAVINGS\",\"currency\":\"INR\",\"openingBalance\":\"1000.5\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/accounts/")))
                .andExpect(jsonPath("$.name").value("HDFC Savings"))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value("1000.5000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString().transform(b -> JsonPath.read(b, "$.id"));

        mvc.perform(get("/api/v1/accounts/{id}/entries", id).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("OPENING"))
                .andExpect(jsonPath("$.items[0].amount").value("1000.5000"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value("1000.5000"));
    }

    @Test
    void usersOnlySeeTheirOwnAccounts() throws Exception {
        String id = idOf(create(alice, cash("Wallet")));

        mvc.perform(get("/api/v1/accounts/{id}", id).with(user(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(get("/api/v1/accounts").with(user(bob))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(patch("/api/v1/accounts/{id}", id).with(user(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activeNamesAreUniquePerUserIgnoringCaseButReusableAfterArchive() throws Exception {
        String id = idOf(create(alice, cash("Wallet")));
        create(alice, cash("WALLET")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACCOUNT_NAME"));
        create(bob, cash("Wallet")).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/accounts/{id}/archive", id).with(user(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        create(alice, cash("Wallet")).andExpect(status().isCreated());
    }

    @Test
    void listsActiveAccountsByDefaultAndArchivedOnRequest() throws Exception {
        idOf(create(alice, cash("A")));
        String archived = idOf(create(alice, cash("B")));
        mvc.perform(post("/api/v1/accounts/{id}/archive", archived).with(user(alice)));

        mvc.perform(get("/api/v1/accounts").with(user(alice))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/accounts").param("includeArchived", "true").with(user(alice)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void renamesAccount() throws Exception {
        String id = idOf(create(alice, cash("Old")));

        mvc.perform(patch("/api/v1/accounts/{id}", id).with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void validatesInput() throws Exception {
        create(alice, "{\"name\":\"X\",\"type\":\"PIGGY_BANK\",\"currency\":\"INR\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_TYPE"));
        create(alice, "{\"name\":\"X\",\"type\":\"CASH\",\"currency\":\"ZZZ\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
        create(alice, "{\"name\":\"X\",\"type\":\"CASH\",\"currency\":\"INR\",\"openingBalance\":\"ten\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        create(alice, "{\"type\":\"CASH\",\"currency\":\"INR\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void manualAdjustmentIsIdempotentPerKey() throws Exception {
        String id = idOf(create(alice, cash("Wallet")));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/accounts/{id}/adjustments", id).with(user(alice))
                            .header("Idempotency-Key", "adj-0001-abcd")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":\"-75.25\",\"description\":\"Cash count\"}"))
                    .andExpect(status().is(i == 0 ? 201 : 200))
                    .andExpect(jsonPath("$.kind").value("ADJUSTMENT"))
                    .andExpect(jsonPath("$.balanceAfter").value("-75.2500"));
        }
        mvc.perform(get("/api/v1/accounts/{id}", id).with(user(alice))).andExpect(jsonPath("$.balance").value("-75.2500"));
        mvc.perform(post("/api/v1/accounts/{id}/adjustments", id).with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void requiresApiToken() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(UUID userId, String json) throws Exception {
        return mvc.perform(post("/api/v1/accounts").with(user(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String cash(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"CASH\",\"currency\":\"INR\"}";
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
