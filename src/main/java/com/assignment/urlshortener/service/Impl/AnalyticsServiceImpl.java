package com.assignment.urlshortener.service.Impl;

import com.assignment.urlshortener.dto.AnalyticsResponse;
import com.assignment.urlshortener.dto.TrendingUrlResponse;
import com.assignment.urlshortener.entity.ClickEvent;
import com.assignment.urlshortener.entity.UrlMapping;
import com.assignment.urlshortener.exception.UrlNotFoundException;
import com.assignment.urlshortener.repository.ClickEventRepository;
import com.assignment.urlshortener.repository.UrlMappingRepository;
import com.assignment.urlshortener.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlMappingRepository  urlMappingRepository;

    @Override
    @Async("analyticsExecutor")
    @Transactional
    public void recordClickAsync(Long urlMappingId, String referrer, String userAgent) {
        log.info("Recording click on thread: {}", Thread.currentThread().getName());
        try{
            ClickEvent clickEvent = ClickEvent.builder()
                    .urlMappingId(urlMappingId)
                    .referrer(referrer)
                    .userAgent(userAgent).build();
            clickEventRepository.save(clickEvent);

            urlMappingRepository.findById(urlMappingId).ifPresent(urlMapping ->
            {
                urlMapping.setClickCount(urlMapping.getClickCount() + 1);
                urlMappingRepository.save(urlMapping);
            });
        }catch (Exception ex){
            // Never let analytics failure affect the user — log and swallow
            log.error("Failed to record click for urlMappingId={}", urlMappingId, ex);
        }
    }

    @Override
    public AnalyticsResponse getStats(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        List<ClickEvent> recent = clickEventRepository.findByUrlMappingId(mapping.getId());

        List<AnalyticsResponse.ClickDetail> clickDetails = recent.stream()
                .sorted((a,b) -> b.getClickedAt().compareTo(a.getClickedAt()))
                .limit(20)
                .map(c -> new AnalyticsResponse.ClickDetail(
                        c.getClickedAt(),
                        c.getReferrer(),
                        c.getUserAgent()
                ))
                .toList();

        return new AnalyticsResponse(shortCode, mapping.getClickCount(), clickDetails);
    }

    @Override
    public List<TrendingUrlResponse> getTrendingUrls(int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Pageable pageable = PageRequest.of(0, limit);

        return clickEventRepository.findTrending(since, pageable).stream()
                .map(p -> {
                    UrlMapping mapping = urlMappingRepository.findById(p.getUrlMappingId())
                            .orElse(null);
                    if (mapping == null) return null;
                    return new TrendingUrlResponse(
                            mapping.getShortCode(), mapping.getOriginalUrl(), p.getClickCount());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
