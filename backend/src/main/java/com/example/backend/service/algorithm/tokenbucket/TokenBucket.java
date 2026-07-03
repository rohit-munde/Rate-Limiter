package com.example.backend.service.algorithm.tokenbucket;

/**
 * Thread-safe class implementing the Token Bucket algorithm.
 */
public class TokenBucket {
    private final int capacity;
    private final double refillRatePerSecond;

    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucket(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    public TokenBucket(int capacity, double refillRatePerSecond, double tokens, long lastRefillTimestamp) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = tokens;
        this.lastRefillTimestamp = lastRefillTimestamp;
    }

    public synchronized void refill() {
        long now = System.currentTimeMillis();
        double elapsedTime = (now - lastRefillTimestamp) / 1000.0;

        if (elapsedTime > 0) {
            tokens = Math.min(capacity, tokens + (elapsedTime * refillRatePerSecond));
            lastRefillTimestamp = now;
        }
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    public synchronized int getRemainingRequests() {
        refill();
        return (int) Math.floor(tokens);
    }

    public synchronized long retryAfterSeconds() {
        refill();
        if (tokens >= 1) {
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / refillRatePerSecond);
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized double getTokens() {
        return tokens;
    }

    public synchronized long getLastRefillTimestamp() {
        return lastRefillTimestamp;
    }
}
