package com.assignment.urlshortener.Controller;

import com.assignment.urlshortener.dto.ShortenRequest;
import com.assignment.urlshortener.dto.ShortenResponse;
import com.assignment.urlshortener.entity.UrlMapping;
import com.assignment.urlshortener.service.AnalyticsService;
import com.assignment.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    @PostMapping("/api/urls")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody @Valid ShortenRequest shortenRequest){
        return ResponseEntity.ok().body(urlService.shortenUrl(shortenRequest));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest httpRequest){
        UrlMapping mapping = urlService.resolveShortCode(shortCode);

        // Fire-and-forget async analytics logging — doesn't block redirect
        analyticsService.recordClickAsync(
                mapping.getId(),
                httpRequest.getHeader("Referer"),
                httpRequest.getHeader("User-Agent")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, mapping.getOriginalUrl());
        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302
    }

    @PatchMapping("/api/urls/{shortCode}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String shortCode) {
        urlService.deactivateUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
