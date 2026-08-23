package com.assignment.urlshortener.service.Impl;

import com.assignment.urlshortener.dto.AnalyticsResponse;
import com.assignment.urlshortener.dto.TrendingUrlResponse;
import com.assignment.urlshortener.entity.UrlMapping;
import com.assignment.urlshortener.exception.UrlNotFoundException;
import com.assignment.urlshortener.repository.ClickEventRepository;
import com.assignment.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AnalyticsServiceImplTest {

    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock private UrlMappingRepository urlMappingRepository;
    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void recordClickAsync_incrementsClickCount_onSuccess() {
        UrlMapping mapping = UrlMapping.builder().id(1L).clickCount(5L).build();
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

        analyticsService.recordClickAsync(1L, "https://google.com", "Mozilla/5.0");

        verify(clickEventRepository, times(1)).save(any());
        assertEquals(6L, mapping.getClickCount());
        verify(urlMappingRepository, times(1)).save(mapping);
    }

    @Test
    void recordClickAsync_doesNotThrow_whenRepositoryFails() {
        when(clickEventRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        // Should NOT propagate — swallowed and logged per reliability design
        assertDoesNotThrow(() -> analyticsService.recordClickAsync(1L, "ref", "ua"));
    }

    @Test
    void recordClickAsync2_doesNotThrow_whenRepositoryFails() {
        when(urlMappingRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        // Should NOT propagate — swallowed and logged per reliability design
        assertDoesNotThrow(() -> analyticsService.recordClickAsync(1L, "ref", "ua"));
    }

    @Test
    void getStats_throwsNotFound_whenShortCodeMissing() {
        when(urlMappingRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> analyticsService.getStats("missing"));
    }

    @Test
    void getStats_returnsCorrectTotalClicks() {
        UrlMapping mapping = UrlMapping.builder().id(1L).shortCode("abc1234").clickCount(10L).build();
        when(urlMappingRepository.findByShortCode("abc1234")).thenReturn(Optional.of(mapping));
        when(clickEventRepository.findByUrlMappingId(1L)).thenReturn(Collections.emptyList());

        AnalyticsResponse response = analyticsService.getStats("abc1234");

        assertEquals(10L, response.getTotalClicks());
    }

    @Test
    void getTrendingUrls_returnsUrlsOrderedByRecentClicks() {
        UrlMapping mapping1 = UrlMapping.builder().id(1L).shortCode("abc111").originalUrl("https://a.com").build();
        UrlMapping mapping2 = UrlMapping.builder().id(2L).shortCode("xyz222").originalUrl("https://b.com").build();

        ClickEventRepository.TrendingProjection p1 = mock(ClickEventRepository.TrendingProjection.class);
        when(p1.getUrlMappingId()).thenReturn(1L);
        when(p1.getClickCount()).thenReturn(15L);

        ClickEventRepository.TrendingProjection p2 = mock(ClickEventRepository.TrendingProjection.class);
        when(p2.getUrlMappingId()).thenReturn(2L);
        when(p2.getClickCount()).thenReturn(8L);

        when(clickEventRepository.findTrending(any(), any())).thenReturn(List.of(p1, p2));
        when(urlMappingRepository.findById(1L)).thenReturn(Optional.of(mapping1));
        when(urlMappingRepository.findById(2L)).thenReturn(Optional.of(mapping2));

        List<TrendingUrlResponse> result = analyticsService.getTrendingUrls(10);

        assertEquals(2, result.size());
        assertEquals("abc111", result.get(0).getShortCode());
        assertEquals(15L, result.get(0).getRecentClicks());
        assertEquals("xyz222", result.get(1).getShortCode());
    }

    @Test
    void getTrendingUrls_skipsOrphanedProjections_whenMappingDeleted() {
        ClickEventRepository.TrendingProjection p1 = mock(ClickEventRepository.TrendingProjection.class);
        when(p1.getUrlMappingId()).thenReturn(99L);
        when(p1.getClickCount()).thenReturn(5L);

        when(clickEventRepository.findTrending(any(), any())).thenReturn(List.of(p1));
        when(urlMappingRepository.findById(99L)).thenReturn(Optional.empty()); // mapping missing

        List<TrendingUrlResponse> result = analyticsService.getTrendingUrls(10);

        assertTrue(result.isEmpty()); // should not throw NPE, should filter out nulls
    }

    @Test
    void getTrendingUrls_returnsEmptyList_whenNoRecentClicks() {
        when(clickEventRepository.findTrending(any(), any())).thenReturn(Collections.emptyList());

        List<TrendingUrlResponse> result = analyticsService.getTrendingUrls(10);

        assertTrue(result.isEmpty());
    }
}
