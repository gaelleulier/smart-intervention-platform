package io.smartip.security;

import io.smartip.domain.UserRole;

public record LoginResponse(String token, String email, UserRole role) {}
