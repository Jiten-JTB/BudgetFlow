package com.budgetflow.identity.adapter.in.security;

import com.budgetflow.identity.application.port.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a sign-in session whose user no longer exists (deleted their account), so the authorization endpoint can never
 * issue tokens for an erased user. The browser then simply sees the sign-in page.
 */
public class DeletedUserSessionFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public DeletedUserSessionFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) && isErased(auth.getName())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isErased(String principal) {
        try {
            return users.findById(UUID.fromString(principal)).isEmpty();
        } catch (IllegalArgumentException notAUserId) {
            return false;
        }
    }
}
