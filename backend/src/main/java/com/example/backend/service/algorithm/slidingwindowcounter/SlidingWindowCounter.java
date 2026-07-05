package com.example.backend.service.algorithm.slidingwindowcounter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SlidingWindowCounter {
    private final int capacity;
    private final long windowSeconds;
    private final Map<Long, Integer> counts = new HashMap<>();

    public SlidingWindowCounter(int capacity, long windowSeconds) {
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    public synchronized boolean tryConsume() {
        long currentWindow = currentWindow();
        long previousWindow = currentWindow - 1;

        removeOldWindows(previousWindow);

        double estimatedCount = estimatedCount(currentWindow, previousWindow);
        if (estimatedCount >= capacity) {
            return false;
        }

        increment(currentWindow);
        return true;
    }

    public synchronized int getRemainingRequests() {
        long currentWindow = currentWindow();
        long previousWindow = currentWindow - 1;

        removeOldWindows(previousWindow);

        double estimatedCount = estimatedCount(currentWindow, previousWindow);
        return Math.max(capacity - (int) Math.ceil(estimatedCount), 0);
    }

    public synchronized long getRetryAfterSeconds() {
        long currentWindow = currentWindow();
        long previousWindow = currentWindow - 1;

        removeOldWindows(previousWindow);

        double estimatedCount = estimatedCount(currentWindow, previousWindow);
        if (estimatedCount < capacity) {
            return 0;
        }

        long windowInMillis = windowSeconds * 1000;
        long elapsedTimeInCurrentWindow = System.currentTimeMillis() % windowInMillis;
        long retryAfterMillis = windowInMillis - elapsedTimeInCurrentWindow;

        return Math.max(1, (long) Math.ceil(retryAfterMillis / 1000.0));
    }

    public int getCapacity() {
        return capacity;
    }

    private double estimatedCount(long currentWindow, long previousWindow) {
        int currentCount = getCount(currentWindow);
        int previousCount = getCount(previousWindow);
        double previousWindowWeight = getPreviousWindowWeight();

        return currentCount + previousCount * previousWindowWeight;
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

    private int getCount(long window) {
        return counts.getOrDefault(window, 0);
    }

    private void increment(long window) {
        int currentCount = getCount(window);
        counts.put(window, currentCount + 1);
    }

    private void removeOldWindows(long oldestWindowToKeep) {
        Iterator<Long> iterator = counts.keySet().iterator();
        while (iterator.hasNext()) {
            long window = iterator.next();
            if (window < oldestWindowToKeep) {
                iterator.remove();
            }
        }
    }
}
