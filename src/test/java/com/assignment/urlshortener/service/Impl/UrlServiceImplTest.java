package com.assignment.urlshortener.service.Impl;

import com.assignment.urlshortener.dto.ShortenRequest;
import com.assignment.urlshortener.dto.ShortenResponse;
import com.assignment.urlshortener.entity.UrlMapping;
import com.assignment.urlshortener.exception.*;
import com.assignment.urlshortener.repository.UrlMappingRepository;
import com.assignment.urlshortener.service.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class UrlServiceImplTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;
    @Mock
    private Base62Encoder base62Encoder;
    @InjectMocks
    private UrlServiceImpl urlServiceImpl;

    @BeforeEach
    void setup(){
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void shortenUrl_generatesUniqueCode_whenNoCustomAlias(){
        ShortenRequest  shortenRequest = new ShortenRequest();
        shortenRequest.setOriginalUrl("https://sample.com");

        when(base62Encoder.encode()).thenReturn("abcd987");
        when(urlMappingRepository.existsByShortCode("abcd987")).thenReturn(false);

        ShortenResponse shortenResponse = urlServiceImpl.shortenUrl(shortenRequest);

        assertEquals("http://localhost:8080/abcd987", shortenResponse.getShortUrl(),"Short url generated is not matching...");
        verify(urlMappingRepository, times(1)).save(any(UrlMapping.class));
    }

    @Test
    void shortenUrl_retriesOnCollision_thenSucceeds(){
        ShortenRequest  shortenRequest = new ShortenRequest();
        shortenRequest.setOriginalUrl("https://sample.com");

        // First two attempts collide, third succeeds
        when(base62Encoder.encode()).thenReturn("abcd987","abcd987", "hui4786");
        when(urlMappingRepository.existsByShortCode("abcd987")).thenReturn(true);
        when(urlMappingRepository.existsByShortCode("hui4786")).thenReturn(false);

        ShortenResponse shortenResponse = urlServiceImpl.shortenUrl(shortenRequest);

        assertEquals("http://localhost:8080/hui4786",  shortenResponse.getShortUrl(),"Short url generated is not matching...");
        verify(base62Encoder,times(3)).encode();
    }

    @Test
    void shortenUrl_throwsAfterMaxRetries_whenAlwaysColliding(){
        ShortenRequest  shortenRequest = new ShortenRequest();
        shortenRequest.setOriginalUrl("https://sample.com");

        when(base62Encoder.encode()).thenReturn("notuniq");
        when(urlMappingRepository.existsByShortCode("notuniq")).thenReturn(true);

        assertThrows(ShortCodeGenerationException.class, () -> urlServiceImpl.shortenUrl(shortenRequest),"Short url not generated after max attempts retry...");
        verify(base62Encoder,times(5)).encode();
    }

    @Test
    void shortenUrl_throwsAliasAlreadyExists_whenCustomAliasTaken() {
        ShortenRequest request = new ShortenRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("myalias");

        when(urlMappingRepository.existsByShortCode("myalias")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class, () -> urlServiceImpl.shortenUrl(request));
        verify(urlMappingRepository, never()).save(any());
    }

    @Test
    void resolveShortCode_returnsMapping_whenValidAndNotExpired() {
        UrlMapping urlMapping = UrlMapping.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("myalias")
                .expiresAt(null)
                .clickCount(0L)
                .active(true).build();

        when(urlMappingRepository.findByShortCode(urlMapping.getShortCode())).thenReturn(Optional.of(urlMapping));
        UrlMapping newUrlMapping=urlServiceImpl.resolveShortCode(urlMapping.getShortCode());

        assertEquals("https://example.com", newUrlMapping.getOriginalUrl(),"Original URL not matching..");
        verify(urlMappingRepository, times(1)).findByShortCode(urlMapping.getShortCode());
    }

    @Test
    void resolveShortCode_throwsNotFound_whenCodeDoesNotExist() {
        UrlMapping urlMapping = UrlMapping.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("nofound")
                .expiresAt(null)
                .clickCount(0L)
                .active(true).build();
        when(urlMappingRepository.findByShortCode(urlMapping.getShortCode())).thenReturn(Optional.empty());
        assertThrows(UrlNotFoundException.class, () -> urlServiceImpl.resolveShortCode(urlMapping.getShortCode()));
    }

    @Test
    void resolveShortCode_throwsExpired_whenPastExpiryDate() {
        UrlMapping expired = UrlMapping.builder()
                .id(1L).shortCode("old1234").originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().minusDays(1)).clickCount(0L).active(true).build();

        when(urlMappingRepository.findByShortCode("old1234")).thenReturn(Optional.of(expired));

        assertThrows(UrlExpiredException.class, () -> urlServiceImpl.resolveShortCode("old1234"));
    }

    @Test
    void resolveShortCode_throwsDeactivated_whenWhenUrlDeactivated() {
        UrlMapping urlMapping = UrlMapping.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("myalias")
                .expiresAt(null)
                .clickCount(0L)
                .active(false).build();

        when(urlMappingRepository.findByShortCode(urlMapping.getShortCode())).thenReturn(Optional.of(urlMapping));

        assertThrows(UrlDeactivatedException.class, () -> urlServiceImpl.resolveShortCode(urlMapping.getShortCode()));
    }

}