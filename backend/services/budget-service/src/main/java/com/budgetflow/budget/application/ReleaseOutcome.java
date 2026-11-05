package com.budgetflow.budget.application;

public enum ReleaseOutcome {
    RELEASED,
    ALREADY_RELEASED,
    /** Nothing was consumed under the key; it is now cancelled so a late consume cannot apply. */
    NOTHING_TO_RELEASE
}
