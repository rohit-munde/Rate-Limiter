package com.example.backend.dto;

/**
 * Record representing the result of a rate limit check.
 */
public record RateLimitResult(
        boolean allowed,
        int capacity,
        int remainingRequests,
        long retryAfterSeconds
) {
    public static RateLimitResult allowed(int capacity, int remainingRequests) {
        return new RateLimitResult(true, capacity, remainingRequests, 0);
    }

    public static RateLimitResult blocked(int capacity, int remainingRequests, long retryAfterSeconds) {
        return new RateLimitResult(false, capacity, remainingRequests, retryAfterSeconds);
    }
}
