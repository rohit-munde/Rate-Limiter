package com.example.backend.service.algorithm.fixedwindow;

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

@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "fixed-window"
)
public class FixedWindowRateLimiterService implements RateLimiterService {
    private final StringRedisTemplate redisTemplate;
    private final int defaultCapacity;
    private final double windowSeconds;
    private final ConcurrentMap<String, FixedWindow> fallbackWindows = new ConcurrentHashMap<>();


    public FixedWindowRateLimiterService(StringRedisTemplate redisTemplate,
                                         @Value("${rate-limiter.fixed-window.limit:10}") int defaultCapacity,
                                         @Value("${rate-limiter.fixed-window.window-seconds:60}") double windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:fixed_window:" + request.clientId() + ":" + request.path() + ":" + request.method();
        try {
            return checkWithRedis(key);
        } catch (DataAccessException e) {
            return checkWithInMemoryFallback(key);
        }
    }

    private RateLimitResult checkWithRedis(String key) {
        String rawValue = redisTemplate.opsForValue().get(key);

        FixedWindow window = rawValue == null
                ? new FixedWindow(defaultCapacity, windowSeconds)
                : restoreWindow(rawValue);

        boolean allowed = window.tryConsume();

        String updatedValue = window.getRequestCount() + "," + window.getWindowStartTimeStamp();
        redisTemplate.opsForValue().set(key, updatedValue, Duration.ofHours(1));

        if (allowed) {
            return RateLimitResult.allowed(window.getCapacity(), window.getRemainingRequests());
        }
        return RateLimitResult.blocked(
                window.getCapacity(),
                window.getRemainingRequests(),
                window.getRetryAfterSeconds()
        );
    }

    private RateLimitResult checkWithInMemoryFallback(String key) {
        FixedWindow window = fallbackWindows.computeIfAbsent(
                key,
                ignored -> new FixedWindow(defaultCapacity, windowSeconds)
        );

        boolean allowed = window.tryConsume();

        if (allowed) {
            return RateLimitResult.allowed(window.getCapacity(), window.getRemainingRequests());
        }
        return RateLimitResult.blocked(
                window.getCapacity(),
                window.getRemainingRequests(),
                window.getRetryAfterSeconds()
        );
    }

    private FixedWindow restoreWindow(String rawValue) {
        String[] parts = rawValue.split(",");
        int requestCount = Integer.parseInt(parts[0]);
        long windowStartTimestamp = Long.parseLong(parts[1]);
        return new FixedWindow(defaultCapacity, windowSeconds, requestCount, windowStartTimestamp);
    }
}
