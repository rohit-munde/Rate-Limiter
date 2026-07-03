package com.example.backend.service.algorithm.tokenbucket;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter service using the Token Bucket algorithm with Redis-backed state.
 */
@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "token-bucket",
        matchIfMissing = true
)
public class TokenBucketRateLimiterService implements RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final int defaultCapacity;
    private final double defaultRefillRatePerSecond;
    private final ConcurrentMap<String, TokenBucket> fallbackBuckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiterService(
            StringRedisTemplate redisTemplate,
            @Value("${rate-limiter.token-bucket.capacity:10}") int defaultCapacity,
            @Value("${rate-limiter.token-bucket.refill-rate-per-second:1.0}") double defaultRefillRatePerSecond
    ) {
        this.redisTemplate = redisTemplate;
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRatePerSecond = defaultRefillRatePerSecond;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:token_bucket:" + request.clientId() + ":" + request.path() + ":" + request.method();
        try {
            return checkWithRedis(key);
        } catch (DataAccessException e) {
            return checkWithInMemoryFallback(key);
        }
    }

    private RateLimitResult checkWithRedis(String key) {
        String rawValue = redisTemplate.opsForValue().get(key);

        TokenBucket bucket = rawValue == null
                ? new TokenBucket(defaultCapacity, defaultRefillRatePerSecond)
                : restoreBucket(rawValue);

        boolean allowed = bucket.tryConsume();

        String updatedValue = bucket.getTokens() + "," + bucket.getLastRefillTimestamp();
        redisTemplate.opsForValue().set(key, updatedValue, Duration.ofHours(1));

        if (allowed) {
            return RateLimitResult.allowed(bucket.getCapacity(), bucket.getRemainingRequests());
        }
        return RateLimitResult.blocked(
                bucket.getCapacity(),
                bucket.getRemainingRequests(),
                bucket.retryAfterSeconds()
        );
    }

    private RateLimitResult checkWithInMemoryFallback(String key) {
        TokenBucket bucket = fallbackBuckets.computeIfAbsent(
                key,
                ignored -> new TokenBucket(defaultCapacity, defaultRefillRatePerSecond)
        );

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

    private TokenBucket restoreBucket(String rawValue) {
        String[] parts = rawValue.split(",");
        double tokens = Double.parseDouble(parts[0]);
        long lastRefill = Long.parseLong(parts[1]);
        return new TokenBucket(defaultCapacity, defaultRefillRatePerSecond, tokens, lastRefill);
    }
}
