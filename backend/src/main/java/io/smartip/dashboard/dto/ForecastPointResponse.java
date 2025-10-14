package io.smartip.dashboard.dto;

import java.time.LocalDate;

public record ForecastPointResponse(LocalDate date, long predictedCount) {}
