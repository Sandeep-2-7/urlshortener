package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.AnalyticsResponse;
import com.assignment.urlshortener.dto.TrendingUrlResponse;

import java.util.List;

public interface AnalyticsService {

    void recordClickAsync(Long urlMappingId, String referrer, String userAgent);
    AnalyticsResponse getStats(String shortCode);
    List<TrendingUrlResponse> getTrendingUrls(int limit);
}
