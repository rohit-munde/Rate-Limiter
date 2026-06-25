package com.example.backend;

import com.example.backend.service.TokenBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void testTokenBucketBehavior() throws InterruptedException {
        // Capacity = 10, Refill rate = 1.0 token per second
        TokenBucket bucket = new TokenBucket(10, 1.0);

        // First 10 rapid requests should be allowed
        for (int i = 0; i < 10; i++) {
            assertTrue(bucket.tryConsume(), "Request " + (i + 1) + " should be allowed");
        }

        // 11th immediate request should return false (blocked)
        assertFalse(bucket.tryConsume(), "11th immediate request should be blocked");

        // Wait 1.1 seconds to ensure the 1.0 token refills completely
        Thread.sleep(1100);

        // Next request should be allowed
        assertTrue(bucket.tryConsume(), "Request after waiting 1 second should be allowed");

        // Immediate next request should be blocked again
        assertFalse(bucket.tryConsume(), "Immediate request after refilled token consumption should be blocked");
    }
}
