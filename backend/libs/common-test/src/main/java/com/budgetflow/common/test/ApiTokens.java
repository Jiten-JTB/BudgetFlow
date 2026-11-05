package com.budgetflow.common.test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** MockMvc authentication as a Budget Flow user, the way the gateway forwards it. */
public final class ApiTokens {

    private ApiTokens() {}

    public static JwtRequestPostProcessor user(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_budgetflow.api"));
    }
}
