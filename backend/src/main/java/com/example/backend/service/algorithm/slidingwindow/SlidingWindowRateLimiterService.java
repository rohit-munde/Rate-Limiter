package com.example.backend.service.algorithm.slidingwindow;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "sliding-window"
)
public class SlidingWindowRateLimiterService implements RateLimiterService {
    private final StringRedisTemplate redisTemplate;
    private final int defaultCapacity;
    private final double windowSeconds;
    private final ConcurrentMap<String, SlidingWindow> fallbackWindows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiterService(StringRedisTemplate redisTemplate,
                                         @Value("${rate-limiter.fixed-window.limit:10}") int defaultCapacity,
                                         @Value("${rate-limiter.fixed-window.window-seconds:60}") double windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }


    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:sliding_window:" + request.clientId() + ":" + request.path() + ":" + request.method();
        try {
            return checkWithRedis(key);
        } catch (DataAccessException e) {
            return checkWithInMemoryFallback(key);
        }
    }

    private RateLimitResult checkWithRedis(String key) {
        String rawValue = redisTemplate.opsForValue().get(key);

        SlidingWindow window = rawValue == null || rawValue.isBlank()
                ? new SlidingWindow(defaultCapacity, windowSeconds)
                : restoreWindow(rawValue);

        boolean allowed = window.tryConsume();

        redisTemplate.opsForValue().set(key, serializeWindow(window), redisTtl());

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
        SlidingWindow window = fallbackWindows.computeIfAbsent(key, k -> new SlidingWindow(defaultCapacity, windowSeconds));

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

    private SlidingWindow restoreWindow(String rawValue) {
        NavigableSet<Long> requestTimestamps = new TreeSet<>();

        for (String part : rawValue.split(",")) {
            if (!part.isBlank()) {
                requestTimestamps.add(Long.parseLong(part));
            }
        }

        return new SlidingWindow(defaultCapacity, windowSeconds, requestTimestamps);
    }

    private String serializeWindow(SlidingWindow window) {
        return window.getRequestTimestamps()
                .stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Duration redisTtl() {
        return Duration.ofMillis(Math.max(1, (long) Math.ceil(windowSeconds * 1000)));
    }
}
