package com.budgetflow.identity.application;

/** Where a request came from, for the audit trail. */
public record ClientInfo(String remoteAddress, String userAgent) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);
}
