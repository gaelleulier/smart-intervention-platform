package io.smartip.users.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record UserPageResponse(
        List<UserResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size) {

    public static UserPageResponse fromPage(Page<UserResponse> page) {
        return new UserPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
