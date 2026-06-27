package com.example.backend.filter;

import com.example.backend.dto.RateLimitRequest;
import com.example.backend.dto.RateLimitResult;
import com.example.backend.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;

/**
 * Filter that intercepts incoming HTTP requests and enforces rate limiting.
 */
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    public RateLimiterFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/gateway/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientId = request.getRemoteAddr();
        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitRequest rateLimitRequest = new RateLimitRequest(clientId, path, method);
        RateLimitResult result = rateLimiterService.check(rateLimitRequest);

        if (result.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.capacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingRequests()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.capacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingRequests()));
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            String jsonResponse = String.format("{\"message\": \"Rate limit exceeded. Try again in %d seconds.\"}", result.retryAfterSeconds());
            response.getWriter().write(jsonResponse);
        }
    }
}
