package com.budgetflow.identity.application;

/** {@code null} fields are left unchanged. */
public record UpdateProfileCommand(String displayName, String baseCurrency, String timezone) {}
