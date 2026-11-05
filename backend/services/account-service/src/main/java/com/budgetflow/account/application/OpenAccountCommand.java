package com.budgetflow.account.application;

/** @param openingBalance decimal string, optional */
public record OpenAccountCommand(String name, String type, String currency, String openingBalance) {}
