package com.budgetflow.identity.config;

import com.budgetflow.identity.adapter.out.mail.EmailOutboxRelay;
import com.budgetflow.identity.adapter.out.mail.JdbcEmailOutbox;
import com.budgetflow.identity.adapter.out.persistence.JdbcVerificationTokenStore;
import com.budgetflow.identity.application.DeleteAccountService;
import com.budgetflow.identity.application.EmailVerificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionTemplate;
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
                                            AuditLog audit, EmailVerificationService verification,
                                            IdentityProperties properties, Clock clock) {
        return new RegisterUserService(users, new SpringPasswordHasher(encoder), new OutboxUserEventPublisher(outbox),
                audit, verification, properties.emailVerification().required(), clock);
    }

    @Bean
    EmailVerificationService emailVerificationService(UserRepository users, JdbcClient jdbc, AuditLog audit,
                                                      IdentityProperties properties, Clock clock) {
        return new EmailVerificationService(users, new JdbcVerificationTokenStore(jdbc), new JdbcEmailOutbox(jdbc, clock),
                audit, clock, properties.webVerifyEmailUrl());
    }

    @Bean
    DeleteAccountService deleteAccountService(UserRepository users, PasswordEncoder encoder, OutboxWriter outbox,
                                              AuditLog audit, Clock clock) {
        return new DeleteAccountService(users, new SpringPasswordHasher(encoder), new OutboxUserEventPublisher(outbox),
                audit, clock);
    }

    /**
     * Sends queued emails. Fails fast at startup when verification is required but no mail server is configured
     * (spring.mail.host), instead of silently stranding every new user.
     */
    @Bean
    EmailOutboxRelay emailOutboxRelay(JdbcClient jdbc, ObjectProvider<JavaMailSender> mail, TransactionTemplate tx,
                                      IdentityProperties properties, Clock clock) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null && properties.emailVerification().required()) {
            throw new IllegalStateException("Email verification is required but no mail server is configured: "
                    + "set spring.mail.host (SPRING_MAIL_HOST) or budgetflow.identity.email-verification.required=false");
        }
        return sender == null ? null : new EmailOutboxRelay(jdbc, sender, tx, clock, properties.mail().from());
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
