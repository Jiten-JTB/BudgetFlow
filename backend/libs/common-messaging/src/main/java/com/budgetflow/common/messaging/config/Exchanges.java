package com.budgetflow.common.messaging.config;

/** Shared topic exchanges. Routing keys: {@code <service>.<name>}, e.g. {@code account.apply-balance-delta}. */
public final class Exchanges {

    /** Saga commands from the orchestrator to participants. */
    public static final String COMMANDS = "budgetflow.commands";
    /** Saga replies from participants to the orchestrator. */
    public static final String REPLIES = "budgetflow.replies";
    /** Domain events (fan-out to reporting, notifications, ...). */
    public static final String EVENTS = "budgetflow.events";

    private Exchanges() {}
}
