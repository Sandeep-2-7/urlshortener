package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.ShortenRequest;
import com.assignment.urlshortener.dto.ShortenResponse;
import com.assignment.urlshortener.entity.UrlMapping;

public interface UrlService {

    ShortenResponse shortenUrl(ShortenRequest shortenRequest);
    UrlMapping resolveShortCode(String shortCode);
    void deactivateUrl(String shortCode);
}
