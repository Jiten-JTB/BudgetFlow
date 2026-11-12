package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Sign-up → email (read from a real SMTP server) → confirm → sign in, end to end inside identity. */
class EmailVerificationIT extends IdentityIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern LINK = Pattern.compile("https://budgetflow\\.test/verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void aNewUserMustConfirmTheirEmailBeforeSigningIn() throws Exception {
        String email = uniqueEmail();
        register(email);

        String text = awaitEmailTo(email);
        assertThat(text).contains("Confirm your email");
        Matcher link = LINK.matcher(text);
        assertThat(link.find()).as("email contains the verification link").isTrue();
        String token = link.group(1);

        // Right password, unconfirmed inbox: told to confirm, not signed in.
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "correct horse battery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/login?unverified"));

        mvc.perform(post("/api/v1/users/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        // A link works once.
        mvc.perform(post("/api/v1/users/email-verifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));

        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "correct horse battery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl()).doesNotContain("/login"));
        assertThat(jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE email = :e").param("e", email)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void theWrongPasswordNeverRevealsThatAnAccountIsUnconfirmed() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "wrong password here"))
                .andExpect(r -> assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/login?error"));
    }

    @Test
    void resendIsSilentForUnknownAddressesAndThrottled() throws Exception {
        mvc.perform(post("/api/v1/users/email-verifications/resend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isAccepted());

        String email = uniqueEmail();
        register(email);
        awaitEmailTo(email);
        mvc.perform(post("/api/v1/users/email-verifications/resend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(1_000);
        assertThat(emailsTo(email)).as("a resend within a minute of the last link sends nothing").hasSize(1);
    }

    @Test
    void aDeliveredEmailLeavesNothingQueued() throws Exception {
        String email = uniqueEmail();
        register(email);
        awaitEmailTo(email);
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.sql("SELECT count(*) FROM email_outbox WHERE recipient = :e")
                .param("e", email).query(Integer.class).single() == 0);
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(REGISTER_JSON.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String awaitEmailTo(String email) {
        await().atMost(Duration.ofSeconds(15)).until(() -> !emailsTo(email).isEmpty());
        String id = emailsTo(email).getFirst();
        return get("/api/v1/message/" + id, "$.Text");
    }

    private List<String> emailsTo(String email) {
        String query = URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8);
        return JsonPath.read(getRaw("/api/v1/search?query=" + query), "$.messages[*].ID");
    }

    private String get(String path, String jsonPath) {
        return JsonPath.read(getRaw(path), jsonPath);
    }

    private String getRaw(String path) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(MAILPIT.apiUrl() + path)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
