package com.budgetflow.identity.adapter.in.security;

import org.springframework.security.authentication.AccountStatusException;

/** Correct password, but the owner has not confirmed their email yet. Raised only after the password check. */
public class EmailNotVerifiedException extends AccountStatusException {

    public EmailNotVerifiedException() {
        super("Email address not verified");
    }
}
