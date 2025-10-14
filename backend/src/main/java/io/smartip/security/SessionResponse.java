package io.smartip.security;

import io.smartip.domain.UserRole;

public record SessionResponse(String email, UserRole role) {}
