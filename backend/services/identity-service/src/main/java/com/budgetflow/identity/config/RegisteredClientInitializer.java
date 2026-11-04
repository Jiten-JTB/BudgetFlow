package com.budgetflow.identity.config;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Upserts first-party OAuth2 clients from configuration on startup, so client setup is declarative and
 * secrets come from the environment. All clients: Authorization Code + PKCE, no consent screen (first party),
 * rotating refresh tokens.
 */
public class RegisteredClientInitializer implements ApplicationRunner {

    private static final java.time.Duration AUTHORIZATION_CODE_TTL = java.time.Duration.ofMinutes(5);

    private final IdentityProperties properties;
    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    RegisteredClientInitializer(IdentityProperties properties, RegisteredClientRepository clients,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.clients().forEach(config -> clients.save(toRegisteredClient(config)));
    }

    private RegisteredClient toRegisteredClient(IdentityProperties.Client config) {
        RegisteredClient existing = clients.findByClientId(config.clientId());
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();

        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(config.clientId())
                .clientName(config.clientName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(config.redirectUris()))
                .postLogoutRedirectUris(uris -> uris.addAll(config.postLogoutRedirectUris()))
                .scopes(scopes -> scopes.addAll(config.scopes()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(AUTHORIZATION_CODE_TTL)
                        .accessTokenTimeToLive(config.accessTokenTtl())
                        .refreshTokenTimeToLive(config.refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build());

        if (config.type() == IdentityProperties.ClientType.CONFIDENTIAL) {
            if (config.clientSecret() == null || config.clientSecret().isBlank()) {
                throw new IllegalStateException("Confidential client '" + config.clientId() + "' requires client-secret");
            }
            builder.clientSecret(passwordEncoder.encode(config.clientSecret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return builder.build();
    }
}
