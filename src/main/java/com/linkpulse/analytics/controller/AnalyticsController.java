
package com.linkpulse.analytics.controller;

import com.linkpulse.analytics.dto.UrlAnalyticsResponse;
import com.linkpulse.analytics.service.AnalyticsService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(
            AnalyticsService analyticsService
    ) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<UrlAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int days
    ) {

        Long userId = Long.parseLong(jwt.getSubject());

        return ResponseEntity.ok(
                analyticsService.getAnalytics(
                        userId,
                        id,
                        days
                )
        );
    }
}
