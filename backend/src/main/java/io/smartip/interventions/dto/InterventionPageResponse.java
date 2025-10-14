package io.smartip.interventions.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record InterventionPageResponse(
        List<InterventionResponse> content, int page, int size, long totalElements, int totalPages) {

    public static InterventionPageResponse fromPage(Page<InterventionResponse> page) {
        return new InterventionPageResponse(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
