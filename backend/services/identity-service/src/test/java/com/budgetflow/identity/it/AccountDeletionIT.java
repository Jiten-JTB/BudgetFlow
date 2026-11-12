package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AccountDeletionIT extends IdentityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void theWrongPasswordKeepsTheAccount() throws Exception {
        String email = uniqueEmail();
        String id = register(email);
        mvc.perform(delete("/api/v1/users/me").with(as(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"not my password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        assertThat(count("SELECT count(*) FROM users WHERE email = :v", email)).isEqualTo(1);
    }

    @Test
    void deletingErasesTheUserTellsOtherServicesAndFreesTheEmail() throws Exception {
        String email = uniqueEmail();
        String id = register(email);
        markEmailVerified(jdbc, email);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf()).param("username", email).param("password", "correct horse battery"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/somewhere").session(session)).andExpect(status().isNotFound()); // signed in: past security

        mvc.perform(delete("/api/v1/users/me").with(as(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"correct horse battery\"}"))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM users WHERE email = :v", email)).isZero();
        assertThat(count("SELECT count(*) FROM email_verification_token WHERE user_id = CAST(:v AS uuid)", id)).isZero();
        assertThat(count("SELECT count(*) FROM auth_audit_event WHERE principal = :v", email)).as("no email left in the audit trail").isZero();
        assertThat(count("SELECT count(*) FROM auth_audit_event WHERE user_id = CAST(:v AS uuid) AND event_type = 'USER_DELETED'", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_message WHERE routing_key = 'identity.user-deleted' AND aggregate_id = :v", id))
                .as("UserDeleted published for the other services").isEqualTo(1);

        // The old browser session no longer counts as signed in.
        mvc.perform(get("/somewhere").session(session)).andExpect(status().is3xxRedirection());

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String register(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_JSON.formatted(email))).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static RequestPostProcessor as(String userId) {
        return jwt().jwt(j -> j.subject(userId)).authorities(new SimpleGrantedAuthority("SCOPE_budgetflow.api"));
    }

    private int count(String sql, String value) {
        return jdbc.sql(sql).param("v", value).query(Integer.class).single();
    }
}
