package com.budgetflow.identity.adapter.out.crypto;

import com.budgetflow.identity.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

public class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
