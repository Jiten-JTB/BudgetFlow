package com.budgetflow.identity.config;

import com.budgetflow.identity.adapter.in.security.LoginOutcomeHandlers;
import com.budgetflow.identity.adapter.in.security.UserDetailsServiceAdapter;
import com.budgetflow.identity.application.LoginAttemptService;
import com.budgetflow.identity.application.port.UserRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Three filter chains, most specific first:
 * 1. OAuth2/OIDC protocol endpoints (Spring Authorization Server).
 * 2. /api/** — stateless JSON API authenticated by our own access tokens.
 * 3. Everything else — browser session with form login (the sign-in page).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        return http
                .oauth2AuthorizationServer(as -> {
                    as.oidc(Customizer.withDefaults());
                    http.securityMatcher(as.getEndpointsMatcher());
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain browserFilterChain(HttpSecurity http, LoginAttemptService loginAttempts) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Actuator is served on the internal management port only (see application.yml).
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers("/login", "/error", "/css/**", "/fonts/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new LoginOutcomeHandlers.Success(loginAttempts))
                        .failureHandler(new LoginOutcomeHandlers.Failure(loginAttempts)))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users, Clock clock) {
        return new UserDetailsServiceAdapter(users, clock);
    }

    /** Argon2id with OWASP 2025 minimums (19 MiB, t=2, p=1); prefix allows future algorithm upgrades. */
    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "argon2";
        return new DelegatingPasswordEncoder(id, Map.of(id, new Argon2PasswordEncoder(16, 32, 1, 19_456, 2)));
    }
}
