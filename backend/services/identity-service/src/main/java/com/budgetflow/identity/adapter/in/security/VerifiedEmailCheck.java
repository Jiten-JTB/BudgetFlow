package com.budgetflow.identity.adapter.in.security;

import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.User;
import java.util.UUID;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

/**
 * Post-authentication check: runs only once the password is right, so "please confirm your email" never tells a
 * stranger that an address has an account.
 */
public class VerifiedEmailCheck implements UserDetailsChecker {

    private final UserRepository users;

    public VerifiedEmailCheck(UserRepository users) {
        this.users = users;
    }

    @Override
    public void check(UserDetails details) {
        if (!details.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("Credentials expired");
        }
        boolean verified = users.findById(UUID.fromString(details.getUsername())).map(User::isEmailVerified).orElse(false);
        if (!verified) {
            throw new EmailNotVerifiedException();
        }
    }
}
