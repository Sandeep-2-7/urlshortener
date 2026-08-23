package com.assignment.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrendingUrlResponse {
    private String shortCode;
    private String originalUrl;
    private long recentClicks;
}