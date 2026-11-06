package com.budgetflow.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SigningKeyFactoryTest {

    @Test
    void loadsPkcs8PemAndDerivesPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        RSAKey key = SigningKeyFactory.create(new IdentityProperties.SigningKey("key-2026-11", pem, false));

        assertThat(key.getKeyID()).isEqualTo("key-2026-11");
        assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    void refusesToStartWithoutKeyUnlessEphemeralAllowed() {
        assertThatThrownBy(() -> SigningKeyFactory.create(new IdentityProperties.SigningKey("k", "", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing-key");
    }

    @Test
    void generatesEphemeralKeyWhenAllowed() throws Exception {
        RSAKey key = SigningKeyFactory.create(new IdentityProperties.SigningKey("dev", null, true));
        RSAKey afterRestart = SigningKeyFactory.create(new IdentityProperties.SigningKey("dev", null, true));

        // A fresh key needs a fresh key id, or verifiers keep using their cached key for that id.
        assertThat(key.getKeyID()).startsWith("dev-ephemeral-");
        assertThat(afterRestart.getKeyID()).isNotEqualTo(key.getKeyID());
        assertThat(key.toRSAPublicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    @Test
    void rejectsMalformedPem() {
        assertThatThrownBy(() -> SigningKeyFactory.create(new IdentityProperties.SigningKey("k", "not a pem", false)))
                .isInstanceOf(IllegalStateException.class);
    }
}
