package io.smartip.dashboard.dto;

import java.time.LocalDate;

public record StatusTrendPoint(LocalDate date, String status, long count) {}
