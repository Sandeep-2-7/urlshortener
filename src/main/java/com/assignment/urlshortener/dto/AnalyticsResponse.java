package com.assignment.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class AnalyticsResponse {
    private String shortCode;
    private long totalClicks;
    private List<ClickDetail> recentClicks;

    @Data
    @AllArgsConstructor
    public static class ClickDetail {
        private LocalDateTime clickedAt;
        private String referrer;
        private String userAgent;
    }
}