
package com.linkpulse.url.dto;

import java.time.Instant;

public record ShortUrlResponse(

        Long id,

        String originalUrl,

        String shortCode,

        String shortUrl,

        boolean active,

        Instant expiresAt,

        long clickCount,

        Instant createdAt,

        Instant updatedAt

) {
}
