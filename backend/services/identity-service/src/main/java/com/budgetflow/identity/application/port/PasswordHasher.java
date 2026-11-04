package com.budgetflow.identity.application.port;

public interface PasswordHasher {

    String hash(String rawPassword);
}
