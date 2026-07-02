package com.example.backend.service.algorithm.leakingbucket;

public class LeakingBucket {

    private final int capacity;
    private final double leakRatePerSecond;

    private double waterLevel;
    private long lastTimeStamp;

    public LeakingBucket(int capacity, double leakRatePerSecond) {
        this.capacity = capacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.lastTimeStamp = System.currentTimeMillis();
    }

    public LeakingBucket(int capacity, double leakRatePerSecond, double waterLevel, long lastTimeStamp) {
        this.capacity = capacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.waterLevel = waterLevel;
        this.lastTimeStamp = lastTimeStamp;
    }

    public synchronized void leak() {
        long now = System.currentTimeMillis();
        double elapsedTime = (now - lastTimeStamp) / 1000.0;

        if (elapsedTime > 0) {
            waterLevel = Math.max(0, waterLevel - (elapsedTime * leakRatePerSecond));
            lastTimeStamp = now;
        }
    }

    public synchronized boolean tryConsume() {
        leak();
        if (waterLevel + 1 <= capacity) {
            waterLevel++;
            return true;
        }
        return false;
    }

    public int getCapacity() {
        return capacity;
    }

    public synchronized int getRemainingCapacity() {
        leak();
        return (int) Math.floor(capacity - waterLevel);
    }

    public synchronized long retryAfterSeconds() {
        leak();
        double spaceNeeded = waterLevel + 1 - capacity;
        if (spaceNeeded <= 0) {
            return 0;
        }
        return (long) Math.ceil(spaceNeeded / leakRatePerSecond);
    }
}
