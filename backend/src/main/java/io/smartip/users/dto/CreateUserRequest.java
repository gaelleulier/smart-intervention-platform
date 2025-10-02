package io.smartip.users.dto;

import io.smartip.users.UserRoleResolver;
import io.smartip.users.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must be at least 8 characters and contain letters and digits")
                String password,
        @Size(max = 20) String role) {

    public UserService.CreateUserCommand toCommand() {
        String sanitizedEmail = email.strip();
        String sanitizedFullName = fullName.strip();
        String sanitizedPassword = password == null ? "" : password.strip();
        return new UserService.CreateUserCommand(
                sanitizedEmail, sanitizedFullName, sanitizedPassword, UserRoleResolver.fromNullable(role));
    }
}
