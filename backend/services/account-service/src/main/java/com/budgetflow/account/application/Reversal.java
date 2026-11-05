package com.budgetflow.account.application;

import com.budgetflow.account.domain.BalanceEntry;

public record Reversal(Outcome outcome, BalanceEntry entry) {

    public enum Outcome {
        REVERSED,
        ALREADY_REVERSED,
        /** The operation never happened; a tombstone now prevents it from ever applying. */
        NOTHING_TO_REVERSE
    }
}
