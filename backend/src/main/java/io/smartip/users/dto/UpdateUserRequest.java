package io.smartip.users.dto;

import io.smartip.users.UserRoleResolver;
import io.smartip.users.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 20) String role,
        @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                        message = "Password must be at least 8 characters and contain letters and digits")
                String password) {

    public UserService.UpdateUserCommand toCommand() {
        String sanitizedEmail = email.strip();
        String sanitizedFullName = fullName.strip();
        String sanitizedPassword = password == null || password.isBlank() ? null : password.strip();
        return new UserService.UpdateUserCommand(
                sanitizedEmail, sanitizedFullName, UserRoleResolver.fromNullable(role), sanitizedPassword);
    }
}
