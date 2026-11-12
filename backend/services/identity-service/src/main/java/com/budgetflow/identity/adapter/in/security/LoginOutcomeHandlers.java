package com.budgetflow.identity.adapter.in.security;

import com.budgetflow.identity.adapter.in.web.ClientInfos;
import com.budgetflow.identity.application.EmailVerificationService;
import com.budgetflow.identity.application.LoginAttemptService;
import com.budgetflow.identity.domain.AuditEventType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/** Form-login hooks feeding lockout counters and the audit trail. The user only ever sees a generic error. */
public final class LoginOutcomeHandlers {

    private static final String USERNAME_PARAMETER = "username";

    private LoginOutcomeHandlers() {}

    public static class Success extends SavedRequestAwareAuthenticationSuccessHandler {
        private final LoginAttemptService attempts;

        public Success(LoginAttemptService attempts) {
            this.attempts = attempts;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) throws ServletException, IOException {
            attempts.recordSuccess(UUID.fromString(authentication.getName()), ClientInfos.from(request));
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    public static class Failure extends SimpleUrlAuthenticationFailureHandler {
        private final LoginAttemptService attempts;
        private final EmailVerificationService verification;

        public Failure(LoginAttemptService attempts, EmailVerificationService verification) {
            super("/login?error");
            this.attempts = attempts;
            this.verification = verification;
        }

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                            AuthenticationException exception) throws IOException, ServletException {
            String login = request.getParameter(USERNAME_PARAMETER);
            var client = ClientInfos.from(request);
            if (exception instanceof EmailNotVerifiedException) {
                // Right password, unconfirmed inbox: send a fresh link (throttled) and say so.
                attempts.recordBlocked(login, AuditEventType.LOGIN_BLOCKED_UNVERIFIED, client);
                verification.resend(login);
                getRedirectStrategy().sendRedirect(request, response, "/login?unverified");
                return;
            }
            switch (exception) {
                case LockedException e -> attempts.recordBlocked(login, AuditEventType.LOGIN_BLOCKED_LOCKED, client);
                case DisabledException e -> attempts.recordBlocked(login, AuditEventType.LOGIN_BLOCKED_DISABLED, client);
                case BadCredentialsException e -> attempts.recordFailure(login, client);
                default -> { /* infrastructure failure: nothing to count */ }
            }
            super.onAuthenticationFailure(request, response, exception);
        }
    }
}
