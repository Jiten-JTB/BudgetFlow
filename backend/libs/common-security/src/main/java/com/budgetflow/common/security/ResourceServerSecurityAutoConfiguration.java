package com.budgetflow.common.security;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Zero-trust defaults for every servlet service: stateless JWT bearer auth (with the {@code budgetflow.api} scope) on all endpoints
 * except health/info probes and API docs. The gateway validates too; services never trust it blindly.
 * Actuator endpoints are open only on the separate management port, which is never routed publicly (Prometheus scrapes it).
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.servlet.OAuth2ResourceServerAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
public class ResourceServerSecurityAutoConfiguration {

    /** Access tokens must carry the API scope; ID tokens or tokens for other audiences are refused. */
    public static final String API_SCOPE = "SCOPE_budgetflow.api";

    static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/error"};

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain resourceServerFilterChain(HttpSecurity http, Environment environment) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(actuatorOnManagementPort(environment)).permitAll()
                        .anyRequest().hasAuthority(API_SCOPE))
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Matches actuator paths only when the request arrived on the socket of a separate management port. Uses the local
     * port of the connection, never a header, and matches nothing when management shares the public port.
     */
    static RequestMatcher actuatorOnManagementPort(Environment environment) {
        return request -> {
            int managementPort = ManagementPort.resolve(environment);
            return managementPort > 0
                    && request.getLocalPort() == managementPort
                    && request.getRequestURI().startsWith("/actuator/");
        };
    }

    @Bean
    WebMvcConfigurer currentUserIdWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserIdArgumentResolver());
            }
        };
    }
}
