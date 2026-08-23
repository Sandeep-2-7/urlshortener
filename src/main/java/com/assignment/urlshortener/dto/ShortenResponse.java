package com.assignment.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ShortenResponse {

    private String shortUrl;

    private String originalUrl;

    private LocalDateTime expiresAt;
}
