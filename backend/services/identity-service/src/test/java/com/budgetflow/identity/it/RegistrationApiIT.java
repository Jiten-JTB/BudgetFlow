package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class RegistrationApiIT extends IdentityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void registersUserWithoutAuthenticationOrCsrfAndNeverReturnsSecrets() throws Exception {
        String email = uniqueEmail();

        String body = register("  " + email.toUpperCase() + " ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.baseCurrency").value("INR"))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE id = CAST(:id AS uuid)")
                .param("id", id).query(String.class).single();
        assertThat(hash).startsWith("{argon2}").doesNotContain("correct horse battery");
    }

    @Test
    void publishesUserRegisteredEventThroughOutboxAndAudits() throws Exception {
        String email = uniqueEmail();
        String id = com.jayway.jsonpath.JsonPath.read(
                register(email).andReturn().getResponse().getContentAsString(), "$.id");

        var outbox = jdbc.sql("""
                SELECT message_type, routing_key, aggregate_id, payload::text AS payload
                FROM outbox_message WHERE aggregate_id = :id""").param("id", id).query().singleRow();
        assertThat(outbox.get("message_type")).isEqualTo("identity.UserRegistered");
        assertThat(outbox.get("routing_key")).isEqualTo("identity.user-registered");
        assertThat((String) outbox.get("payload")).contains(id).contains(email).doesNotContain("password");

        long audits = jdbc.sql("""
                SELECT count(*) FROM auth_audit_event
                WHERE user_id = CAST(:id AS uuid) AND event_type = 'USER_REGISTERED'""")
                .param("id", id).query(Long.class).single();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        register(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void rejectsWeakPasswordInvalidTimezoneAndMissingFields() throws Exception {
        post(REGISTER_JSON.formatted(uniqueEmail()).replace("correct horse battery", "short"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        post(REGISTER_JSON.formatted(uniqueEmail()).replace("Asia/Kolkata", "Mars/Olympus"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        post("{\"email\":\"a@example.com\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions register(String email) throws Exception {
        return post(REGISTER_JSON.formatted(email));
    }

    private ResultActions post(String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
