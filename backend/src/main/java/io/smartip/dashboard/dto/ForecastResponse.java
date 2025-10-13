package io.smartip.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record ForecastResponse(
        Instant generatedAt,
        String method,
        double smoothingFactor,
        long lastObservedCount,
        double baselineAverage,
        List<ForecastPointResponse> points) {}
