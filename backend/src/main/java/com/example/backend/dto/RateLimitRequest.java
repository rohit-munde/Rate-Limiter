package com.example.backend.dto;

/**
 * Record representing an incoming request details for rate limit checking.
 */
public record RateLimitRequest(
        String clientId,
        String path,
        String method
) {}
