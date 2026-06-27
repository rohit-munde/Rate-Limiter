package com.example.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simple test endpoint to verify the rate limiting functionality.
 */
@RestController
@RequestMapping("/gateway")
public class TestController {

    @GetMapping("/health")
    public Map<String, String> hello() {
        return Map.of("status", "UP", "service", "rate-limiter-gateway");
    }
}
