package com.example.backend.service.algorithm.fixedwindow;

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
        havingValue = "fixed-window"
)
public class FixedWindowRateLimiterService implements RateLimiterService {
    private final int defaultCapacity;
    private final double windowSeconds;
    private final ConcurrentMap<String, FixedWindow> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiterService(
            @Value("${rate-limiter.fixed-window.limit:10}") int defaultCapacity,
            @Value("${rate-limiter.fixed-window.window-seconds:60}") double windowSeconds
    ) {
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:fixed_window:" + request.clientId() + ":" + request.path() + ":" + request.method();

        FixedWindow window = windows.get(key);
        if (window == null) {
            FixedWindow newWindow = new FixedWindow(defaultCapacity, windowSeconds);
            FixedWindow existingWindow = windows.putIfAbsent(key, newWindow);
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
