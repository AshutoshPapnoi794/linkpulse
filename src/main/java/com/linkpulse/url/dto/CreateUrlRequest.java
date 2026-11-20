
package com.linkpulse.url.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(

        @NotBlank(message = "Original URL is required")
        @Size(max = 2048, message = "URL cannot exceed 2048 characters")
        String originalUrl,

        @Pattern(
                regexp = "^[A-Za-z0-9_-]{3,16}$",
                message = "Custom alias must contain 3-16 letters, numbers, underscores or hyphens"
        )
        String customAlias,

        @Future(message = "Expiration must be in the future")
        Instant expiresAt

) {
}
