package com.example.backend.service;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;

/**
 * Service interface for checking rate limits.
 */
public interface RateLimiterService {
    RateLimitResult check(RateLimitRequest request);
}
