package com.linkpulse.analytics.dto;

import java.util.List;

public record UrlAnalyticsResponse(
        Long urlId,
        String shortCode,
        long totalClicks,
        int periodDays,
        List<DailyClickResponse> dailyClicks
) {
}