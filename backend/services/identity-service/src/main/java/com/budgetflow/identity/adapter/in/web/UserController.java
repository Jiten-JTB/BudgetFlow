package com.budgetflow.identity.adapter.in.web;

import com.budgetflow.common.security.CurrentUserId;
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

    UserController(RegisterUserService registerUser, ProfileService profiles) {
        this.registerUser = registerUser;
        this.profiles = profiles;
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
