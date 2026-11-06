package com.budgetflow.identity.it;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The branded sign-in page that replaces Spring's default one. */
class LoginPageIT extends IdentityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void rendersBrandedFormWithCsrfAndSignUpLink() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(allOf(
                        containsString("<title>Sign in · Budget Flow</title>"),
                        containsString("name=\"username\""),
                        containsString("name=\"password\""),
                        containsString("name=\"_csrf\""),
                        containsString("href=\"https://budgetflow.test/signup\""),
                        containsString("/css/login.css"),
                        not(containsString("That email and password")))));
    }

    @Test
    void showsOneGenericMessageForWrongPasswordAndLockedAccounts() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "That email and password don’t match. After 5 wrong tries, sign-in pauses for 15 minutes.")));
    }

    @Test
    void confirmsSignOut() throws Exception {
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(containsString("You’re signed out.")));
    }

    @Test
    void servesItsOwnStylesAndFontsWithoutSignIn() throws Exception {
        mvc.perform(get("/css/login.css")).andExpect(status().isOk());
        mvc.perform(get("/fonts/mukta-600.woff2")).andExpect(status().isOk());
    }
}
