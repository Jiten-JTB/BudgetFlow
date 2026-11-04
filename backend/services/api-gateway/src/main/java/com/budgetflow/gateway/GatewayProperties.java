package com.budgetflow.gateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Route URIs and rate limits are referenced from application.yml placeholders. */
@ConfigurationProperties("budgetflow.gateway")
@Validated
public record GatewayProperties(@NotBlank String identityUri, @NotNull @Valid Jwt jwt, @NotNull @Valid Cors cors) {

    /**
     * @param issuer    public issuer that must appear in {@code iss} (budgetflow-auth)
     * @param jwkSetUri internal URL of identity-service's JWKS (no public round-trip)
     */
    public record Jwt(@NotBlank String issuer, @NotBlank String jwkSetUri) {}

    public record Cors(@NotEmpty List<String> allowedOrigins) {}
}
