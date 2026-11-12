package com.budgetflow.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("budgetflow.identity")
@Validated
public record IdentityProperties(
        @NotBlank String issuer,
        @NotNull @Valid SigningKey signingKey,
        Lockout lockout,
        List<@Valid Client> clients,
        String webSignupUrl,
        String webVerifyEmailUrl,
        EmailVerification emailVerification,
        Mail mail) {

    public IdentityProperties {
        webSignupUrl = webSignupUrl == null || webSignupUrl.isBlank() ? "http://localhost:3000/signup" : webSignupUrl;
        webVerifyEmailUrl = webVerifyEmailUrl == null || webVerifyEmailUrl.isBlank()
                ? "http://localhost:3000/verify-email" : webVerifyEmailUrl;
        emailVerification = emailVerification == null ? new EmailVerification(true) : emailVerification;
        mail = mail == null ? new Mail(null) : mail;
        lockout = lockout == null ? new Lockout(null, null) : lockout;
        clients = clients == null ? List.of() : clients;
    }

    /**
     * @param privateKey     PKCS#8 PEM RSA private key (from a secret, never the repo)
     * @param allowEphemeral local/test only: generate a throwaway key when none is configured
     */
    public record SigningKey(@NotBlank String keyId, String privateKey, boolean allowEphemeral) {}

    /** @param required false only where no mail server exists; new users then start verified */
    public record EmailVerification(boolean required) {}

    /** @param from sender address and name, e.g. {@code Budget Flow <jiten@jitenjtb.com>} */
    public record Mail(String from) {
        public Mail {
            from = from == null || from.isBlank() ? "Budget Flow <no-reply@localhost>" : from;
        }
    }

    public record Lockout(Integer maxFailedAttempts, Duration lockDuration) {
        public Lockout {
            maxFailedAttempts = maxFailedAttempts == null ? 5 : maxFailedAttempts;
            lockDuration = lockDuration == null ? Duration.ofMinutes(15) : lockDuration;
        }
    }

    public enum ClientType { CONFIDENTIAL, PUBLIC }

    public record Client(
            @NotBlank String clientId,
            @NotBlank String clientName,
            @NotNull ClientType type,
            String clientSecret,
            @NotNull List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            @NotNull List<String> scopes,
            Duration accessTokenTtl,
            Duration refreshTokenTtl) {

        public Client {
            postLogoutRedirectUris = postLogoutRedirectUris == null ? List.of() : postLogoutRedirectUris;
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
            refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", type=" + type + ", clientSecret=****]";
        }
    }
}
