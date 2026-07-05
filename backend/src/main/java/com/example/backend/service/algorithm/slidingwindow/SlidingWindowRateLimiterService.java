package com.example.backend.service.algorithm.slidingwindow;

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
        havingValue = "sliding-window"
)
public class SlidingWindowRateLimiterService implements RateLimiterService {
    private final int defaultCapacity;
    private final double windowSeconds;
    private final ConcurrentMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiterService(
            @Value("${rate-limiter.sliding-window.limit:10}") int defaultCapacity,
            @Value("${rate-limiter.sliding-window.window-seconds:60}") double windowSeconds
    ) {
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:sliding_window:" + request.clientId() + ":" + request.path() + ":" + request.method();

        SlidingWindow window = windows.get(key);
        if (window == null) {
            SlidingWindow newWindow = new SlidingWindow(defaultCapacity, windowSeconds);
            SlidingWindow existingWindow = windows.putIfAbsent(key, newWindow);
            window = existingWindow == null ? newWindow : existingWindow;
        }

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
}
