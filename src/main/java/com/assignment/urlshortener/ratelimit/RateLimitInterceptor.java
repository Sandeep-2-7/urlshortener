package com.assignment.urlshortener.ratelimit;

import com.assignment.urlshortener.exception.RateLimitExceedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${app.rate-limit.max-requests-per-minute:20}")
    private int maxRequestsPerMinute;

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIP = request.getRemoteAddr();
        AtomicInteger count = requestCounts.get(clientIP, ip -> new AtomicInteger(0));

        if (count.incrementAndGet() > maxRequestsPerMinute) {
            throw new RateLimitExceedException("Rate limit exceeded. Max " + maxRequestsPerMinute + " requests per minute.");
        }
        return true;
    }
}
