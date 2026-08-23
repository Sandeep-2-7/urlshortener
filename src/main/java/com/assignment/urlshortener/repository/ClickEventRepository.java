package com.assignment.urlshortener.repository;

import com.assignment.urlshortener.entity.ClickEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByUrlMappingId(Long urlMappingId);

    long countByUrlMappingId(Long urlMappingId);

    @Query("SELECT\n" +
            "    c.urlMappingId AS urlMappingId,\n" +
            "    COUNT(*) AS clickCount\n" +
            "FROM ClickEvent c\n" +
            "WHERE c.clickedAt >= :since\n" +
            "GROUP BY c.urlMappingId\n" +
            "ORDER BY COUNT(*) DESC")
    List<TrendingProjection> findTrending(@Param("since") LocalDateTime since, Pageable pageable);

    interface TrendingProjection {
        Long getUrlMappingId();
        Long getClickCount();
    }
}