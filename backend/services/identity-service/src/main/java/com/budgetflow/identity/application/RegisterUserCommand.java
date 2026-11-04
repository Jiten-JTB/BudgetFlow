package com.budgetflow.identity.application;

public record RegisterUserCommand(String email, String password, String displayName, String baseCurrency, String timezone) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", password=****]";
    }
}
