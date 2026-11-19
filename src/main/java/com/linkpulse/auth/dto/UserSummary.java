package com.linkpulse.auth.dto;

public record UserSummary(
        Long id,
        String email,
        String role
) {
}
