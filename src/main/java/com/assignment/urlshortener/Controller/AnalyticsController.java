package com.assignment.urlshortener.Controller;


import com.assignment.urlshortener.dto.AnalyticsResponse;
import com.assignment.urlshortener.dto.TrendingUrlResponse;
import com.assignment.urlshortener.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/urls")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}/stats")
    public AnalyticsResponse getStats(@PathVariable String shortCode) {
        return analyticsService.getStats(shortCode);
    }

    @GetMapping("/trending")
    public List<TrendingUrlResponse> trending(@RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTrendingUrls(limit);
    }
}