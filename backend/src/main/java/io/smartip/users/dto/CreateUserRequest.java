package io.smartip.users.dto;

import io.smartip.users.UserRoleResolver;
import io.smartip.users.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 20) String role) {

    public UserService.CreateUserCommand toCommand() {
        String sanitizedEmail = email.strip();
        String sanitizedFullName = fullName.strip();
        return new UserService.CreateUserCommand(
                sanitizedEmail, sanitizedFullName, UserRoleResolver.fromNullable(role));
    }
}
