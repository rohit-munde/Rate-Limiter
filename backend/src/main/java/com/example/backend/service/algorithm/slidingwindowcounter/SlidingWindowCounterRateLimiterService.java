package com.example.backend.service.algorithm.slidingwindowcounter;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "sliding-window-counter"
)
public class SlidingWindowCounterRateLimiterService implements RateLimiterService {
    private final StringRedisTemplate redisTemplate;
    private final int defaultCapacity;
    private final long windowSeconds;

    public SlidingWindowCounterRateLimiterService(StringRedisTemplate redisTemplate,
                                                  @Value("${rate-limiter.fixed-window.limit:10}") int defaultCapacity,
                                                  @Value("${rate-limiter.fixed-window.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.defaultCapacity = defaultCapacity;
        this.windowSeconds = windowSeconds;
    }


    @Override
    public RateLimitResult check(RateLimitRequest request) {
        String key = "rate_limit:sliding_window_counter:" + request.clientId() + ":" + request.path() + ":" + request.method();

        return checkWithRedis(key);
    }

    private RateLimitResult checkWithRedis(String key) {
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        long currWindow = currentTimeMillis/windowInMillis;
        long prevWindow = currWindow - 1;
        //1,2,3,4,5
        // 60, 120, 180..

        String currKey = key + ":" + currWindow;
        String prevKey = key + ":" + prevWindow;

        int currCount = getCount(currKey);
        int prevCount = getCount(prevKey);

        double prevWindowWeight = getPrevWindowWeight();

        double estimatedCount = currCount + prevCount * prevWindowWeight; // <- Core logic for the sliding Window Counter Algorithm

        if(estimatedCount >= defaultCapacity) {
            return RateLimitResult.blocked(defaultCapacity, 0, retryAfterSeconds(estimatedCount));
        }

        Long updatedCount = redisTemplate.opsForValue().increment(currKey, 1);

        if(updatedCount == 1) {
            redisTemplate.expire(currKey, Duration.ofSeconds(windowSeconds * 2));
        }

        double updatedEstimatedCount = estimatedCount + 1;
        int remainingRequests = Math.max(defaultCapacity - (int) Math.ceil(updatedEstimatedCount),0);

        return RateLimitResult.allowed(defaultCapacity, remainingRequests);
    }

    private double getPrevWindowWeight() {
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        long elapsedTimeInCurrentWindow = currentTimeMillis % windowInMillis;

        return (double) (windowInMillis - elapsedTimeInCurrentWindow) / windowInMillis;
    }

    private long retryAfterSeconds(double estimatedCount) {
        if(estimatedCount < defaultCapacity) {
            return 0;
        }
        long currentTimeMillis = System.currentTimeMillis();
        long windowInMillis = windowSeconds * 1000;
        long elapsedTimeInCurrentWindow = currentTimeMillis % windowInMillis;

        long retryAfterMillis = windowInMillis - elapsedTimeInCurrentWindow;

        return Math.max(1, (long) Math.ceil(retryAfterMillis / 1000.0));
    }

    private int getCount(String key) {
        String count  = redisTemplate.opsForValue().get(key);
        return count == null ? 0 : Integer.parseInt(count);
    }
}
