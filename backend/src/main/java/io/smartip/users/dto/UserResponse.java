package io.smartip.users.dto;

import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRole;
import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, UserRole role, Instant createdAt) {

    public static UserResponse fromEntity(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getRole(),
                entity.getCreatedAt());
    }
}
