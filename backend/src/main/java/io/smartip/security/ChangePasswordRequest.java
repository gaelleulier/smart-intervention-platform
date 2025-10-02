package io.smartip.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
                @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                        message = "Password must be at least 8 characters and contain letters and digits")
                String newPassword) {}
