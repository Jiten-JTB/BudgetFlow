package com.budgetflow.identity.config;

import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.identity.adapter.out.crypto.SpringPasswordHasher;
import com.budgetflow.identity.adapter.out.messaging.OutboxUserEventPublisher;
import com.budgetflow.identity.adapter.out.persistence.AuthorizationCleanupJob;
import com.budgetflow.identity.adapter.out.persistence.JdbcAuditLog;
import com.budgetflow.identity.adapter.out.persistence.JdbcUserRepository;
import com.budgetflow.identity.application.LoginAttemptService;
import com.budgetflow.identity.application.ProfileService;
import com.budgetflow.identity.application.RegisterUserService;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.LockoutPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Wires use cases to adapters; the application layer stays free of component scanning. */
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UserRepository userRepository(JdbcClient jdbc) {
        return new JdbcUserRepository(jdbc);
    }

    @Bean
    AuditLog auditLog(JdbcClient jdbc) {
        return new JdbcAuditLog(jdbc);
    }

    @Bean
    RegisterUserService registerUserService(UserRepository users, PasswordEncoder encoder, OutboxWriter outbox,
                                            AuditLog audit, Clock clock) {
        return new RegisterUserService(users, new SpringPasswordHasher(encoder), new OutboxUserEventPublisher(outbox),
                audit, clock);
    }

    @Bean
    ProfileService profileService(UserRepository users, AuditLog audit, Clock clock) {
        return new ProfileService(users, audit, clock);
    }

    @Bean
    LoginAttemptService loginAttemptService(UserRepository users, AuditLog audit, IdentityProperties properties,
                                            Clock clock) {
        var lockout = properties.lockout();
        return new LoginAttemptService(users, audit,
                new LockoutPolicy(lockout.maxFailedAttempts(), lockout.lockDuration()), clock);
    }

    @Bean
    AuthorizationCleanupJob authorizationCleanupJob(JdbcClient jdbc) {
        return new AuthorizationCleanupJob(jdbc);
    }
}
