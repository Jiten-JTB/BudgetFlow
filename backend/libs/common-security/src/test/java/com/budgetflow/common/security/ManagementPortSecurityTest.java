package com.budgetflow.common.security;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prometheus scrapes the internal management port without a token; the public port never serves actuator anonymously. */
@WebMvcTest(controllers = ManagementPortSecurityTest.TestController.class, properties = "management.server.port=9191")
@ImportAutoConfiguration(ResourceServerSecurityAutoConfiguration.class)
class ManagementPortSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void actuatorEndpointsOnTheManagementPortNeedNoToken() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(onPort(9191))).andExpect(status().isOk());
    }

    @Test
    void actuatorEndpointsOnThePublicPortStillNeedAToken() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(onPort(8080))).andExpect(status().isUnauthorized());
    }

    @Test
    void theManagementPortOnlyOpensActuatorPaths() throws Exception {
        mvc.perform(get("/things").with(onPort(9191))).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor onPort(int port) {
        return request -> {
            request.setLocalPort(port);
            request.setServerPort(port);
            return request;
        };
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
        @GetMapping("/actuator/prometheus")
        String metrics() {
            return "jvm_info 1";
        }

        @GetMapping("/things")
        String things() {
            return "ok";
        }
    }
}
