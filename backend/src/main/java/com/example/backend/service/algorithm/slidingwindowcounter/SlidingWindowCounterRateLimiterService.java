package com.example.backend.service.algorithm.slidingwindowcounter;

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
        havingValue = "sliding-window-counter"
)
public class SlidingWindowCounterRateLimiterService implements RateLimiterService {
    private final int defaultCapacity;
    private final long windowSeconds;
    private final ConcurrentMap<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiterService(
            @Value("${rate-limiter.sliding-window-counter.limit:10}") int defaultCapacity,
            @Value("${rate-limiter.sliding-window-counter.window-seconds:60}") long windowSeconds
    ) {
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:sliding_window_counter:" + request.clientId() + ":" + request.path() + ":" + request.method();

        SlidingWindowCounter counter = counters.get(key);
        if (counter == null) {
            SlidingWindowCounter newCounter = new SlidingWindowCounter(defaultCapacity, windowSeconds);
            SlidingWindowCounter existingCounter = counters.putIfAbsent(key, newCounter);
            counter = existingCounter == null ? newCounter : existingCounter;
        }

        boolean allowed = counter.tryConsume();

        if (allowed) {
            return RateLimitResult.allowed(counter.getCapacity(), counter.getRemainingRequests());
        }

        return RateLimitResult.blocked(
                counter.getCapacity(),
                counter.getRemainingRequests(),
                counter.getRetryAfterSeconds()
        );
    }
}
