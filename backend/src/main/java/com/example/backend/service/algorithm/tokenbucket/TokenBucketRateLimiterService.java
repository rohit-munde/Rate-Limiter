package com.example.backend.service.algorithm.tokenbucket;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter service using the Token Bucket algorithm with in-memory state.
 */
@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "token-bucket",
        matchIfMissing = true
)
public class TokenBucketRateLimiterService implements RateLimiterService {

    private final int defaultCapacity;
    private final double defaultRefillRatePerSecond;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiterService(
            @Value("${rate-limiter.token-bucket.capacity:10}") int defaultCapacity,
            @Value("${rate-limiter.token-bucket.refill-rate-per-second:1.0}") double defaultRefillRatePerSecond
    ) {
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRatePerSecond = defaultRefillRatePerSecond;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:token_bucket:" + request.clientId() + ":" + request.path() + ":" + request.method();

        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            TokenBucket newBucket = new TokenBucket(defaultCapacity, defaultRefillRatePerSecond);
            TokenBucket existingBucket = buckets.putIfAbsent(key, newBucket);
            bucket = existingBucket == null ? newBucket : existingBucket;
        }

        boolean allowed = bucket.tryConsume();

        if (allowed) {
            return RateLimitResult.allowed(bucket.getCapacity(), bucket.getRemainingRequests());
        }
        return RateLimitResult.blocked(
                bucket.getCapacity(),
                bucket.getRemainingRequests(),
                bucket.retryAfterSeconds()
        );
    }
}
