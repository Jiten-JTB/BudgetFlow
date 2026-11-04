package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

class LoginLockoutIT extends IdentityIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void locksAccountAfterFiveFailuresEvenForCorrectPasswordThenUnlocksAfterDuration() throws Exception {
        String email = uniqueEmail();
        String userId = register(email);

        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong password " + i)).isEqualTo("/login?error");
        }
        assertThat(login(email, PASSWORD)).as("locked: correct password still refused").isEqualTo("/login?error");

        assertThat(jdbc.sql("SELECT failed_login_attempts FROM users WHERE id = CAST(:id AS uuid)")
                .param("id", userId).query(Integer.class).single()).isEqualTo(5);
        assertThat(auditCount(userId, "LOGIN_FAILURE")).isEqualTo(5);
        assertThat(auditCount(userId, "ACCOUNT_LOCKED")).isEqualTo(1);
        assertThat(auditCount(userId, "LOGIN_BLOCKED_LOCKED")).isEqualTo(1);

        jdbc.sql("UPDATE users SET locked_until = now() - interval '1 second' WHERE id = CAST(:id AS uuid)")
                .param("id", userId).update();
        assertThat(login(email, PASSWORD)).isNotEqualTo("/login?error");
        assertThat(jdbc.sql("SELECT failed_login_attempts FROM users WHERE id = CAST(:id AS uuid)")
                .param("id", userId).query(Integer.class).single()).isZero();
        assertThat(auditCount(userId, "LOGIN_SUCCESS")).isEqualTo(1);
    }

    @Test
    void unknownEmailFailsLikeWrongPasswordAndIsAudited() throws Exception {
        String unknown = uniqueEmail();

        assertThat(login(unknown, PASSWORD)).isEqualTo("/login?error");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM auth_audit_event
                WHERE principal = :p AND user_id IS NULL AND event_type = 'LOGIN_FAILURE'""")
                .param("p", unknown).query(Long.class).single()).isEqualTo(1);
    }

    private String register(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_JSON.formatted(email))).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("username", email).param("password", password))
                .andReturn().getResponse().getRedirectedUrl();
    }

    private long auditCount(String userId, String type) {
        return jdbc.sql("SELECT count(*) FROM auth_audit_event WHERE user_id = CAST(:id AS uuid) AND event_type = :t")
                .param("id", userId).param("t", type).query(Long.class).single();
    }
}
