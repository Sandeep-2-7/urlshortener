package com.assignment.urlshortener.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Caffeine settings already externalized in application.properties
    // (spring.cache.caffeine.spec) — no bean override needed
}
