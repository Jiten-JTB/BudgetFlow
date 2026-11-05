package com.budgetflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayIT.Containers.class)
class GatewayIT {

    private static final StubUpstream UPSTREAM;
    private static final StubUpstream ACCOUNTS;
    private static final StubUpstream TRANSACTIONS;
    private static final StubUpstream BUDGETS;
    private static final String ALLOWED_ORIGIN = "https://budgetflow.test";

    static {
        try {
            UPSTREAM = new StubUpstream();
            ACCOUNTS = new StubUpstream();
            TRANSACTIONS = new StubUpstream();
            BUDGETS = new StubUpstream();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("budgetflow.gateway.identity-uri", UPSTREAM::baseUrl);
        registry.add("budgetflow.gateway.account-uri", ACCOUNTS::baseUrl);
        registry.add("budgetflow.gateway.transaction-uri", TRANSACTIONS::baseUrl);
        registry.add("budgetflow.gateway.budget-uri", BUDGETS::baseUrl);
        registry.add("budgetflow.gateway.jwt.issuer", () -> StubUpstream.ISSUER);
        registry.add("budgetflow.gateway.jwt.jwk-set-uri", () -> UPSTREAM.baseUrl() + "/oauth2/jwks");
        registry.add("budgetflow.gateway.cors.allowed-origins", () -> ALLOWED_ORIGIN);
        // Registration: burst of 2, effectively no refill during the test.
        registry.add("budgetflow.gateway.rate-limit.registration.replenish-rate", () -> 1);
        registry.add("budgetflow.gateway.rate-limit.registration.burst-capacity", () -> 2);
        registry.add("budgetflow.gateway.rate-limit.registration.requested-tokens", () -> 1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
        }
    }

    @LocalServerPort int port;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        UPSTREAM.proxiedRequests.set(0);
    }

    @Test
    void rejectsProtectedRouteWithoutTokenAndDoesNotForward() {
        client.get().uri("/api/v1/users/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");
        assertThat(UPSTREAM.proxiedRequests).hasValue(0);
    }

    @Test
    void forwardsAuthenticatedRequestWithBearerTokenAndGeneratedRequestId() throws Exception {
        String token = UPSTREAM.validToken(UUID.randomUUID().toString());

        String requestId = client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/v1/users/me")
                .returnResult().getResponseHeaders().getFirst("X-Request-Id");

        assertThat(requestId).isNotBlank();
        assertThat(UPSTREAM.lastHeaders.get("authorization")).isEqualTo("Bearer " + token);
        assertThat(UPSTREAM.lastHeaders.get("x-request-id")).isEqualTo(requestId);
    }

    @Test
    void preservesValidClientRequestIdAndReplacesUnsafeOne() throws Exception {
        String token = UPSTREAM.validToken(UUID.randomUUID().toString());

        client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Request-Id", "web-1234abcd").exchange()
                .expectHeader().valueEquals("X-Request-Id", "web-1234abcd");
        assertThat(UPSTREAM.lastHeaders.get("x-request-id")).isEqualTo("web-1234abcd");

        String replaced = client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Request-Id", "bad id with spaces").exchange()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Request-Id");
        assertThat(replaced).isNotEqualTo("bad id with spaces").matches("[0-9a-f-]{36}");
    }

    @Test
    void rejectsTokensWithWrongIssuerExpiredOrForeignSignature() throws Exception {
        String sub = UUID.randomUUID().toString();
        List<String> scopes = List.of("budgetflow.api");
        for (String bad : List.of(
                UPSTREAM.token(sub, "https://evil.test", Instant.now().plusSeconds(300), scopes),
                UPSTREAM.token(sub, StubUpstream.ISSUER, Instant.now().minusSeconds(300), scopes),
                StubUpstream.sign(new RSAKeyGenerator(2048).keyID("gw-test").generate(), sub, StubUpstream.ISSUER,
                        Instant.now().plusSeconds(300), scopes))) {
            client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bad).exchange()
                    .expectStatus().isUnauthorized();
        }
        assertThat(UPSTREAM.proxiedRequests).hasValue(0);
    }

    @Test
    void rejectsTokenWithoutApiScope() throws Exception {
        String token = UPSTREAM.token(UUID.randomUUID().toString(), StubUpstream.ISSUER,
                Instant.now().plusSeconds(300), List.of("openid"));

        client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void registrationIsPublicAndRateLimitedPerClientIp() {
        for (int i = 0; i < 2; i++) {
            register("198.51.100.1").expectStatus().isOk();
        }
        register("198.51.100.1").expectStatus().isEqualTo(429);
        register("198.51.100.2").expectStatus().isOk(); // other IP has its own bucket
    }

    @Test
    void allowsCorsPreflightOnlyFromConfiguredOrigin() {
        client.options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
        client.options().uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://evil.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isForbidden();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "/api/v1/accounts, ACCOUNTS",
        "/api/v1/accounts/0198a3f2-7c1e-7d3a-9b2e-5f6a7b8c9d0e/entries, ACCOUNTS",
        "/api/v1/transactions, TRANSACTIONS",
        "/api/v1/categories, TRANSACTIONS",
        "/api/v1/income-sources, TRANSACTIONS",
        "/api/v1/budgets/status, BUDGETS"
    })
    void routesEachApiToItsOwningService(String path, String service) throws Exception {
        StubUpstream target = switch (service) {
            case "ACCOUNTS" -> ACCOUNTS;
            case "TRANSACTIONS" -> TRANSACTIONS;
            default -> BUDGETS;
        };
        int before = target.proxiedRequests.get();

        client.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + UPSTREAM.validToken("u")).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo(path);

        assertThat(target.proxiedRequests.get()).isEqualTo(before + 1);
    }

    @Test
    void returnsNotFoundForUnknownRoutes() throws Exception {
        client.get().uri("/api/v1/nope").header(HttpHeaders.AUTHORIZATION, "Bearer " + UPSTREAM.validToken("u"))
                .exchange().expectStatus().isNotFound();
    }

    private WebTestClient.ResponseSpec register(String clientIp) {
        return client.post().uri("/api/v1/users")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@example.com\"}")
                .exchange();
    }
}
