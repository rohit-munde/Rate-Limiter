package com.example.backend.service;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service that manages token buckets backed by Redis key-value storage.
 */
@Service
public class InMemoryTokenBucketRateLimiterService implements RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final int defaultCapacity = 10;
    private final double defaultRefillRatePerSecond = 1.0;

    public InMemoryTokenBucketRateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        // Create unique key
        String key = "rate_limit:" + request.clientId() + ":" + request.path() + ":" + request.method();

        // 1. Fetch raw state from Redis
        String rawValue = redisTemplate.opsForValue().get(key);
        TokenBucket bucket;

        if (rawValue == null) {
            // If it doesn't exist, create a new one
            bucket = new TokenBucket(defaultCapacity, defaultRefillRatePerSecond);
        } else {
            // If it exists, split and restore the TokenBucket
            String[] parts = rawValue.split(",");
            double tokens = Double.parseDouble(parts[0]);
            long lastRefill = Long.parseLong(parts[1]);
            bucket = new TokenBucket(defaultCapacity, defaultRefillRatePerSecond, tokens, lastRefill);
        }

        // 2. Consume a token (runs the math and updates internal state)
        boolean allowed = bucket.tryConsume();

        // 3. Save the updated state back to Redis with 1 hour TTL
        String updatedValue = bucket.getTokens() + "," + bucket.getLastRefillTimestamp();
        redisTemplate.opsForValue().set(key, updatedValue, Duration.ofHours(1));

        // 4. Return result
        if (allowed) {
            return RateLimitResult.allowed(bucket.getCapacity(), bucket.getRemainingTokens());
        } else {
            return RateLimitResult.blocked(bucket.getCapacity(), bucket.getRemainingTokens(), bucket.retryAfterSeconds());
        }
    }
}
