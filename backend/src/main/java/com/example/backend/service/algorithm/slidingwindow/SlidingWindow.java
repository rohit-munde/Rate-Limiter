package com.example.backend.service.algorithm.slidingwindow;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.TreeSet;

public class SlidingWindow {
    private final int capacity;
    private final double secondsInSlidingWindow;
    private final NavigableSet<Long> requestTimestamps;

    public SlidingWindow(int capacity, double secondsInSlidingWindow) {
        this.capacity = capacity;
        this.secondsInSlidingWindow = secondsInSlidingWindow;
        this.requestTimestamps = new TreeSet<>();
    }

    public SlidingWindow(int capacity, double secondsInSlidingWindow, Collection<Long> requestTimestamps) {
        this.capacity = capacity;
        this.secondsInSlidingWindow = secondsInSlidingWindow;
        this.requestTimestamps = new TreeSet<>(requestTimestamps);
        removeExpiredTimeStamps();
    }

    public synchronized boolean tryConsume() {
        removeExpiredTimeStamps();
        if (requestTimestamps.size() < capacity) {
            requestTimestamps.add(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    private synchronized void removeExpiredTimeStamps() {
        long now = System.currentTimeMillis();
        long windowStartTimeStamp = now - (long) (secondsInSlidingWindow * 1000);
        requestTimestamps.headSet(windowStartTimeStamp, true).clear();
    }

    public synchronized int getRemainingRequests() {
        removeExpiredTimeStamps();
        return capacity - requestTimestamps.size();
    }

    public synchronized long getRetryAfterSeconds() {
        removeExpiredTimeStamps();
        if (requestTimestamps.size() < capacity) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long oldestRequestTimeStamp = requestTimestamps.first();
        long retryAfterMillis = oldestRequestTimeStamp + (long)( secondsInSlidingWindow * 1000) - now;
        return Math.max(1, (long) Math.ceil(retryAfterMillis/1000.0));
    }

    public int getRequestCount() {
        return requestTimestamps.size();
    }

    public synchronized NavigableSet<Long> getRequestTimestamps() {
        removeExpiredTimeStamps();
        return new TreeSet<>(requestTimestamps);
    }

    public int getCapacity() {
        return capacity;
    }
}
