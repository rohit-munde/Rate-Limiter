package com.example.backend.service.algorithm.slidingwindowcounter;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

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
        long currentWindow = currentWindow();
        long previousWindow = currentWindow - 1;

        WindowCounter counter = counters.get(key);
        if (counter == null) {
            WindowCounter newCounter = new WindowCounter();
            WindowCounter existingCounter = counters.putIfAbsent(key, newCounter);
            counter = existingCounter == null ? newCounter : existingCounter;
        }

        counter.removeOldWindows(previousWindow);

        int currentCount = counter.getCount(currentWindow);
        int previousCount = counter.getCount(previousWindow);
        double previousWindowWeight = getPreviousWindowWeight();
        double estimatedCount = currentCount + previousCount * previousWindowWeight;

        if (estimatedCount >= defaultCapacity) {
            return RateLimitResult.blocked(defaultCapacity, 0, retryAfterSeconds(estimatedCount));
        }

        counter.increment(currentWindow);

        double updatedEstimatedCount = estimatedCount + 1;
        int remainingRequests = Math.max(defaultCapacity - (int) Math.ceil(updatedEstimatedCount), 0);

        return RateLimitResult.allowed(defaultCapacity, remainingRequests);
    }

    private long currentWindow() {
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        return currentTimeMillis / windowInMillis;
    }

    private double getPreviousWindowWeight() {
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        long elapsedTimeInCurrentWindow = currentTimeMillis % windowInMillis;

        return (double) (windowInMillis - elapsedTimeInCurrentWindow) / windowInMillis;
    }

    private long retryAfterSeconds(double estimatedCount) {
        if (estimatedCount < defaultCapacity) {
            return 0;
        }
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        long elapsedTimeInCurrentWindow = currentTimeMillis % windowInMillis;
        long retryAfterMillis = windowInMillis - elapsedTimeInCurrentWindow;

        return Math.max(1, (long) Math.ceil(retryAfterMillis / 1000.0));
    }

    private static class WindowCounter {
        private final Map<Long, Integer> counts = new HashMap<>();

        private synchronized int getCount(long window) {
            return counts.getOrDefault(window, 0);
        }

        private synchronized void increment(long window) {
            int currentCount = getCount(window);
            counts.put(window, currentCount + 1);
        }

        private synchronized void removeOldWindows(long oldestWindowToKeep) {
            Iterator<Long> iterator = counts.keySet().iterator();
            while (iterator.hasNext()) {
                long window = iterator.next();
                if (window < oldestWindowToKeep) {
                    iterator.remove();
                }
            }
        }
    }
}
