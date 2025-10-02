package io.smartip.users;

import io.smartip.domain.UserRole;

public final class UserRoleResolver {

    private UserRoleResolver() {}

    public static UserRole fromNullable(String value) {
        UserRole parsed = parseOrNull(value);
        return parsed != null ? parsed : UserRole.TECH;
    }

    public static UserRole parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUserRoleException(value);
        }
    }
}
