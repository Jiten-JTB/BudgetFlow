package com.budgetflow.common.security;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ResourceServerSecurityTest.TestController.class)
@ImportAutoConfiguration(ResourceServerSecurityAutoConfiguration.class)
class ResourceServerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void resolvesCurrentUserIdFromJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(get("/me").with(apiJwt(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
    }

    @Test
    void rejectsTokenWhoseSubjectIsNotAUserId() throws Exception {
        mvc.perform(get("/me").with(apiJwt("not-a-uuid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void isStatelessAndAcceptsBearerPostsWithoutCsrfToken() throws Exception {
        mvc.perform(post("/things").with(apiJwt(UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void rejectsTokenWithoutApiScope() throws Exception {
        mvc.perform(get("/me").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("SCOPE_openid"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor apiJwt(String subject) {
        return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("SCOPE_budgetflow.api"));
    }

    @SpringBootConfiguration
    @Import(TestController.class)
    static class TestApp {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/me")
        String me(@CurrentUserId UUID userId) {
            return userId.toString();
        }

        @PostMapping("/things")
        String create() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }
}
