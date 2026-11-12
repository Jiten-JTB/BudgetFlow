package com.budgetflow.gateway;

import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class GatewayConfig {

    static final String API_SCOPE = "SCOPE_budgetflow.api";

    /**
     * Edge authentication. Services validate the token again (zero trust); the gateway rejects early so
     * unauthenticated traffic never reaches them.
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, Environment environment) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)              // bearer tokens only, no cookies
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/email-verifications",
                                "/api/v1/users/email-verifications/resend").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .matchers(actuatorOnManagementPort(environment)).permitAll()
                        .anyExchange().hasAuthority(API_SCOPE))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Actuator paths are open only on the separate management port (never routed publicly; Prometheus scrapes it).
     * Uses the connection's local port, never a header, and matches nothing when management shares the public port.
     */
    static ServerWebExchangeMatcher actuatorOnManagementPort(Environment environment) {
        return exchange -> {
            int managementPort = managementPort(environment);
            InetSocketAddress local = exchange.getRequest().getLocalAddress();
            boolean matches = managementPort > 0 && local != null && local.getPort() == managementPort
                    && exchange.getRequest().getPath().value().startsWith("/actuator/");
            return matches ? ServerWebExchangeMatcher.MatchResult.match() : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private static int managementPort(Environment environment) {
        int port = environment.getProperty("local.management.port", Integer.class,
                environment.getProperty("management.server.port", Integer.class, -1));
        int serverPort = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
        return port > 0 && port != serverPort ? port : -1;
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwt().jwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.addAllowedMethod("*");
        cors.addAllowedHeader("*");
        cors.addExposedHeader(RequestIdFilter.HEADER);
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /** Authenticated routes: limit per user, falling back to client IP. */
    @Bean
    @Primary
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.defer(() -> clientIp(exchange)));
    }

    /**
     * Anonymous routes (registration). The remote address honours X-Forwarded-For via
     * server.forward-headers-strategy; Traefik overwrites that header from untrusted clients.
     */
    @Bean
    KeyResolver clientIpKeyResolver() {
        return GatewayConfig::clientIp;
    }

    private static Mono<String> clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        // Forwarded addresses are unresolved (no InetAddress), so use the host string, never DNS.
        return Mono.just("ip:" + (remote == null ? "unknown" : remote.getHostString()));
    }
}
