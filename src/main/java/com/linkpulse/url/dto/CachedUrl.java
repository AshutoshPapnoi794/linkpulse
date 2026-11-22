package com.linkpulse.url.dto;

import java.time.Instant;

public record CachedUrl(
        Long id,
        String originalUrl,
        boolean active,
        Instant expiresAt
) {
}
