package com.example.backend.dto;

/**
 * Record representing the result of a rate limit check.
 */
public record RateLimitResult(
        boolean allowed,
        int capacity,
        int remainingTokens,
        long retryAfterSeconds
) {
    public static RateLimitResult allowed(int capacity, int remainingTokens) {
        return new RateLimitResult(true, capacity, remainingTokens, 0);
    }

    public static RateLimitResult blocked(int capacity, int remainingTokens, long retryAfterSeconds) {
        return new RateLimitResult(false, capacity, remainingTokens, retryAfterSeconds);
    }
}
