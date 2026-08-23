package com.assignment.urlshortener.service.Impl;

import com.assignment.urlshortener.dto.ShortenRequest;
import com.assignment.urlshortener.dto.ShortenResponse;
import com.assignment.urlshortener.entity.UrlMapping;
import com.assignment.urlshortener.exception.*;
import com.assignment.urlshortener.repository.UrlMappingRepository;
import com.assignment.urlshortener.service.Base62Encoder;
import com.assignment.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
@Slf4j
public class UrlServiceImpl implements UrlService {

    private final UrlMappingRepository urlMappingRepository;
    private final Base62Encoder base62Encoder;
    private static final String BASE_URL = "http://localhost:8080/";

    @Override
    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest shortenRequest){

        LocalDateTime expiresAt = shortenRequest.getExpiryInDays() != null ? LocalDateTime.now().plusDays(shortenRequest.getExpiryInDays()) : null;

        UrlMapping mapping = UrlMapping.builder()
                .originalUrl(shortenRequest.getOriginalUrl())
                .expiresAt(expiresAt)
                .customAlias(shortenRequest.getCustomAlias()!=null).build();


        String shortCode;

        if(shortenRequest.getCustomAlias()!=null && !shortenRequest.getCustomAlias().isBlank()){
            if(urlMappingRepository.existsByShortCode(shortenRequest.getCustomAlias())){
                throw new AliasAlreadyExistsException(shortenRequest.getCustomAlias());
            }
                shortCode=shortenRequest.getCustomAlias();
        }
        else{
            shortCode= generateUniqueCode();
        }

        mapping.setShortCode(shortCode);
        urlMappingRepository.save(mapping);

        return new ShortenResponse(BASE_URL+shortCode, mapping.getOriginalUrl(), expiresAt);
    }

    private String generateUniqueCode(){
        int maxAttempts = 5;

        for(int attempt=0;attempt<5;attempt++){
            String code = base62Encoder.encode();
            if(!urlMappingRepository.existsByShortCode(code)){
                return code;
            }
            log.warn("Collision occured on generating short code in the attempt {} : {}", attempt, code);
        }
        throw new ShortCodeGenerationException("Failed to generate unique short code after " + maxAttempts + " attempts");
    }

    @Override
    @Cacheable(value = "urlCache", key = "#shortCode")
    public UrlMapping resolveShortCode(String shortCode) {

        UrlMapping saved = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if(!saved.getActive())
            throw new UrlDeactivatedException(shortCode);

        if(saved.getExpiresAt() != null && saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlExpiredException(shortCode);
        }
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "urlCache", key = "#shortCode")
    public void deactivateUrl(String shortCode) {
        UrlMapping urlMapping = urlMappingRepository.findByShortCode(shortCode).orElseThrow(() -> new UrlNotFoundException(shortCode));
        urlMapping.setActive(false);
        urlMappingRepository.save(urlMapping);

    }
}
