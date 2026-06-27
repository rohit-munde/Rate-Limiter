package com.example.backend.service.algorithm.fixedwindow;

public class FixedWindow {
    private final int capacity;
    private final double secondsInFixedWindow;
    private int requestCount;
    private long windowStartTimeStamp;

    public FixedWindow(int capacity, double secondsInFixedWindow) {
        this.capacity = capacity;
        this.secondsInFixedWindow = secondsInFixedWindow;
        this.requestCount = 0;
        this.windowStartTimeStamp = System.currentTimeMillis();
    }

    public FixedWindow(int capacity, double secondsInFixedWindow, int requestCount, long windowStartTimeStamp) {
        this.capacity = capacity;
        this.secondsInFixedWindow = secondsInFixedWindow;
        this.requestCount = requestCount;
        this.windowStartTimeStamp = windowStartTimeStamp;
    }

    public synchronized void resetWindowIfNeeded() {
        long now = System.currentTimeMillis();
        double elapsedTime = (now - windowStartTimeStamp) / 1000.0;

        if (elapsedTime >= secondsInFixedWindow) {
            requestCount = 0;
            windowStartTimeStamp = now;
        }
    }

    public synchronized boolean tryConsume() {
        resetWindowIfNeeded();
        if (requestCount < capacity) {
            requestCount++;
            return true;
        }
        return false;
    }

    public synchronized int getRemainingRequests() {
        resetWindowIfNeeded();
        return capacity - requestCount;
    }

    public synchronized long getRetryAfterSeconds() {
        resetWindowIfNeeded();
        if (requestCount < capacity) {
            return 0;
        }
        long now = System.currentTimeMillis();
        double elapsedTime = (now - windowStartTimeStamp) / 1000.0;
        return Math.max(1, (long) Math.ceil(secondsInFixedWindow - elapsedTime));
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public long getWindowStartTimeStamp() {
        return windowStartTimeStamp;
    }
}
