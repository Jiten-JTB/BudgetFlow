package com.budgetflow.identity.adapter.in.web;

import com.budgetflow.common.security.CurrentUserId;
import com.budgetflow.identity.application.DeleteAccountService;
import com.budgetflow.identity.application.EmailVerificationService;
import com.budgetflow.identity.application.ProfileService;
import com.budgetflow.identity.application.RegisterUserCommand;
import com.budgetflow.identity.application.RegisterUserService;
import com.budgetflow.identity.application.UpdateProfileCommand;
import com.budgetflow.identity.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final RegisterUserService registerUser;
    private final ProfileService profiles;
    private final EmailVerificationService verification;
    private final DeleteAccountService deleteAccount;

    UserController(RegisterUserService registerUser, ProfileService profiles, EmailVerificationService verification,
                   DeleteAccountService deleteAccount) {
        this.registerUser = registerUser;
        this.profiles = profiles;
        this.verification = verification;
        this.deleteAccount = deleteAccount;
    }

    /** Public; rate limited at the gateway. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterUserRequest request, HttpServletRequest http) {
        User user = registerUser.register(new RegisterUserCommand(request.email(), request.password(),
                request.displayName(), request.baseCurrency(), request.timezone()), ClientInfos.from(http));
        return UserResponse.of(user);
    }

    @GetMapping("/me")
    UserResponse me(@CurrentUserId UUID userId) {
        return UserResponse.of(profiles.get(userId));
    }

    @PatchMapping("/me")
    UserResponse updateMe(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request,
                          HttpServletRequest http) {
        return UserResponse.of(profiles.update(userId, new UpdateProfileCommand(
                request.displayName(), request.baseCurrency(), request.timezone()), ClientInfos.from(http)));
    }

    /** Permanently erases the signed-in user after they confirm with their password. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe(@CurrentUserId UUID userId, @Valid @RequestBody DeleteAccountRequest request, HttpServletRequest http) {
        deleteAccount.delete(userId, request.password(), ClientInfos.from(http));
    }

    /** Public: the link from the verification email. Rate limited per IP at the gateway. */
    @PostMapping("/email-verifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        verification.confirm(request.token());
    }

    /** Public and deliberately silent: always 202, whether or not the address has an account. */
    @PostMapping("/email-verifications/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        verification.resend(request.email());
    }

    record DeleteAccountRequest(@NotBlank @Size(max = 1024) String password) {
        @Override
        public String toString() {
            return "DeleteAccountRequest[password=****]";
        }
    }

    record ConfirmEmailRequest(@NotBlank @Size(max = 200) String token) {
        @Override
        public String toString() {
            return "ConfirmEmailRequest[token=****]";
        }
    }

    record ResendVerificationRequest(@NotBlank @Size(max = 320) String email) {}

    // Size caps are coarse input guards; the domain enforces the real rules.
    record RegisterUserRequest(
            @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(max = 1024) String password,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(max = 16) String baseCurrency,
            @NotBlank @Size(max = 64) String timezone) {

        @Override
        public String toString() {
            return "RegisterUserRequest[email=" + email + ", password=****]";
        }
    }

    record UpdateProfileRequest(
            @Size(max = 200) String displayName,
            @Size(max = 16) String baseCurrency,
            @Size(max = 64) String timezone) {}

    record UserResponse(UUID id, String email, String displayName, String baseCurrency, String timezone,
                        Instant createdAt) {
        static UserResponse of(User user) {
            return new UserResponse(user.id(), user.email().value(), user.displayName(),
                    user.baseCurrency().getCurrencyCode(), user.timezone().getId(), user.createdAt());
        }
    }
}
