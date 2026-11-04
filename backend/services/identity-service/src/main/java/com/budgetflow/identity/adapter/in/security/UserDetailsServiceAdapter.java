package com.budgetflow.identity.adapter.in.security;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.User;
import com.budgetflow.identity.domain.UserStatus;
import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Login is by email, but the authenticated principal name is the user id, so every token's
 * {@code sub} is the stable UUID other services key their data on.
 */
public class UserDetailsServiceAdapter implements UserDetailsService {

    private final UserRepository users;
    private final Clock clock;

    public UserDetailsServiceAdapter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        Email email;
        try {
            email = Email.of(login);
        } catch (DomainException e) {
            throw new UsernameNotFoundException("User not found");
        }
        User user = users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.withUsername(user.id().toString())
                .password(user.passwordHash())
                .accountLocked(user.isLockedAt(clock.instant()))
                .disabled(user.status() == UserStatus.DISABLED)
                .authorities("ROLE_USER")
                .build();
    }
}
