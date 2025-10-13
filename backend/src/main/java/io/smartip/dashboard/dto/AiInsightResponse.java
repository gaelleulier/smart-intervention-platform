package io.smartip.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record AiInsightResponse(
        LocalDate date,
        String headline,
        String trendDirection,
        double trendPercentage,
        double validationRate,
        String validationAssessment,
        String slaAssessment,
        List<String> highlights) {}
