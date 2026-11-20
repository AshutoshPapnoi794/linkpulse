
package com.linkpulse.url.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateUrlRequest(

        @NotNull(message = "Expiration date is required")
        @Future(message = "Expiration must be in the future")
        Instant expiresAt

) {
}
