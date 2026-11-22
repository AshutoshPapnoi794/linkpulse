package com.linkpulse.analytics.dto;

import java.time.LocalDate;

public record DailyClickResponse(
        LocalDate date,
        long clicks
) {
}