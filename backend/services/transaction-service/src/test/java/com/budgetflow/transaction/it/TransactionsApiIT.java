package com.budgetflow.transaction.it;

import static com.budgetflow.common.test.ApiTokens.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

class TransactionsApiIT extends TransactionIntegrationTest {

    @Autowired JdbcClient jdbc;

    private final UUID alice = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private String food;

    @BeforeEach
    void setUp() throws Exception {
        food = createCategory(alice, "Food", "EXPENSE");
    }

    @Test
    void acceptsTransactionAsPendingAndEmitsSagaRequest() throws Exception {
        String body = record(alice, "key-accept-0001", expense("250.75", "2026-11-03"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/transactions/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value("250.7500"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        var outbox = jdbc.sql("""
                SELECT message_type, routing_key, payload::text AS payload FROM outbox_message
                WHERE aggregate_id = :id""").param("id", id).query().singleRow();
        assertThat(outbox.get("message_type")).isEqualTo("transaction.TransactionRequested");
        assertThat(outbox.get("routing_key")).isEqualTo("transaction.transaction-requested");
        assertThat((String) outbox.get("payload"))
                .contains("\"transactionId\":\"" + id + "\"", "\"amount\":\"250.7500\"", "\"kind\":\"EXPENSE\"",
                        "\"accountId\":\"" + account + "\"", "\"occurredOn\":\"2026-11-03\"");
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutDuplicating() throws Exception {
        String first = id(record(alice, "key-replay-0001", expense("10", "2026-11-03")).andExpect(status().isAccepted()));
        String second = id(record(alice, "key-replay-0001", expense("10", "2026-11-03")).andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        record(alice, "key-replay-0001", expense("11", "2026-11-03"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        record(alice, null, expense("10", "2026-11-03"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void validatesShapeAndValues() throws Exception {
        record(alice, "key-valid-0001", "{\"kind\":\"TRANSFER\",\"accountId\":\"" + account + "\",\"amount\":\"5\","
                + "\"currency\":\"INR\",\"occurredOn\":\"2026-11-03\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TRANSACTION"));
        record(alice, "key-valid-0002", expense("abc", "2026-11-03"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        record(alice, "key-valid-0003", expense("5", "03/11/2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE"));
        record(alice, "key-valid-0004", "{\"kind\":\"GIFT\",\"accountId\":\"" + account + "\",\"amount\":\"5\","
                + "\"currency\":\"INR\",\"occurredOn\":\"2026-11-03\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_KIND"));
    }

    @Test
    void listsNewestFirstWithFiltersAndCursorPaging() throws Exception {
        UUID otherAccount = UUID.randomUUID();
        for (int day = 1; day <= 5; day++) {
            record(alice, "key-list-000" + day, expense(String.valueOf(day), "2026-10-0" + day));
        }
        record(alice, "key-list-0099", "{\"kind\":\"TRANSFER\",\"accountId\":\"" + account + "\",\"counterAccountId\":\""
                + otherAccount + "\",\"amount\":\"100\",\"currency\":\"INR\",\"occurredOn\":\"2026-10-03\"}")
                .andExpect(status().isAccepted());

        String page1 = mvc.perform(get("/api/v1/transactions").param("kind", "EXPENSE").param("limit", "2")
                        .param("from", "2026-10-01").param("to", "2026-10-31").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].occurredOn").value("2026-10-05"))
                .andExpect(jsonPath("$.items[1].occurredOn").value("2026-10-04"))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(page1, "$.nextCursor");

        mvc.perform(get("/api/v1/transactions").param("kind", "EXPENSE").param("limit", "2").param("cursor", cursor)
                        .param("from", "2026-10-01").param("to", "2026-10-31").with(user(alice)))
                .andExpect(jsonPath("$.items[0].occurredOn").value("2026-10-03"))
                .andExpect(jsonPath("$.items[1].occurredOn").value("2026-10-02"));
        mvc.perform(get("/api/v1/transactions").param("categoryId", food).param("from", "2026-10-04")
                        .with(user(alice)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get("/api/v1/transactions").param("cursor", "garbage!!").with(user(alice)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void transactionsAreIsolatedPerUser() throws Exception {
        String id = id(record(alice, "key-iso-00001", expense("10", "2026-11-03")));
        UUID bob = UUID.randomUUID();

        mvc.perform(get("/api/v1/transactions/{id}", id).with(user(bob))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
        mvc.perform(get("/api/v1/transactions").with(user(bob))).andExpect(jsonPath("$.items", hasSize(0)));
        record(bob, "key-iso-00002", expense("10", "2026-11-03"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    private ResultActions record(UUID userId, String key, String json) throws Exception {
        var request = post("/api/v1/transactions").with(user(userId)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private String expense(String amount, String date) {
        return "{\"kind\":\"EXPENSE\",\"accountId\":\"" + account + "\",\"amount\":\"" + amount + "\",\"currency\":\"INR\","
                + "\"categoryId\":\"" + food + "\",\"occurredOn\":\"" + date + "\",\"description\":\"Lunch\"}";
    }

}
