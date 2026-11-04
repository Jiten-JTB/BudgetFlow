package com.budgetflow.identity.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads the token signing key. Production refuses to start without a configured key. */
final class SigningKeyFactory {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyFactory.class);
    private static final int EPHEMERAL_KEY_BITS = 2048;

    private SigningKeyFactory() {}

    static RSAKey create(IdentityProperties.SigningKey config) {
        if (config.privateKey() != null && !config.privateKey().isBlank()) {
            return fromPem(config.keyId(), config.privateKey());
        }
        if (config.allowEphemeral()) {
            log.warn("Using an EPHEMERAL signing key '{}': tokens become invalid on restart. Never use in production.",
                    config.keyId());
            return ephemeral(config.keyId());
        }
        throw new IllegalStateException(
                "budgetflow.identity.signing-key.private-key is required (PKCS#8 PEM RSA key)");
    }

    private static RSAKey fromPem(String keyId, String pem) {
        try {
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            var publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return build(keyId, publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid budgetflow.identity.signing-key.private-key (expected PKCS#8 PEM RSA)", e);
        }
    }

    private static RSAKey ephemeral(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(EPHEMERAL_KEY_BITS);
            var pair = generator.generateKeyPair();
            return build(keyId, (RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate ephemeral signing key", e);
        }
    }

    private static RSAKey build(String keyId, RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }
}
