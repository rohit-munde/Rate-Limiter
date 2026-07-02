package com.example.backend.service.algorithm.leakingbucket;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "leaking-bucket"
)
public class LeakingBucketRateLimiterService implements RateLimiterService {
    private final int defaultCapacity;
    private final double leakRate;
    private final ConcurrentMap<String, LeakingBucket> buckets = new ConcurrentHashMap<>();

    public LeakingBucketRateLimiterService(@Value("${rate-limiter.leaking-bucket.capacity:5}") int defaultCapacity,
                                           @Value("${rate-limiter.leaking-bucket.leaking-rate:1}") double leakRate) {
        this.defaultCapacity = defaultCapacity;
        this.leakRate = leakRate;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:leaking_bucket:" + request.clientId() + ":" + request.path() + ":" + request.method();
        LeakingBucket bucket = buckets.computeIfAbsent(key, ignored -> new LeakingBucket(defaultCapacity, leakRate));

        boolean allowed = bucket.tryConsume();
        if (allowed) {
            return RateLimitResult.allowed(bucket.getCapacity(), bucket.getRemainingCapacity());
        }

        return RateLimitResult.blocked(bucket.getCapacity(), bucket.getRemainingCapacity(), bucket.retryAfterSeconds());
    }
}
