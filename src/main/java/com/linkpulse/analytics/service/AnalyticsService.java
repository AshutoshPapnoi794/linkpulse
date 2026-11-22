
package com.linkpulse.analytics.service;

import com.linkpulse.analytics.dto.DailyClickResponse;
import com.linkpulse.analytics.dto.UrlAnalyticsResponse;
import com.linkpulse.analytics.repository.ClickEventRepository;

import com.linkpulse.url.entity.ShortUrl;
import com.linkpulse.url.exception.UrlNotFoundException;
import com.linkpulse.url.repository.ShortUrlRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(
            Long userId,
            Long urlId,
            int days
    ) {

        if (days < 1 || days > 90) {
            throw new IllegalArgumentException(
                    "Analytics period must be between 1 and 90 days"
            );
        }

        ShortUrl shortUrl = shortUrlRepository
                .findByIdAndUser_Id(urlId, userId)
                .orElseThrow(UrlNotFoundException::new);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        LocalDate startDate = today.minusDays(days - 1L);

        Instant from = startDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Instant to = today
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        List<DailyClickResponse> recordedClicks =
                clickEventRepository
                        .findDailyClicks(urlId, from, to)
                        .stream()
                        .map(result -> new DailyClickResponse(
                                result.getClickDate(),
                                result.getClicks()
                        ))
                        .toList();

        Map<LocalDate, Long> clickCounts =
                recordedClicks.stream()
                        .collect(Collectors.toMap(
                                DailyClickResponse::date,
                                DailyClickResponse::clicks
                        ));

        List<DailyClickResponse> dailyClicks =
                startDate.datesUntil(today.plusDays(1))
                        .map(date -> new DailyClickResponse(
                                date,
                                clickCounts.getOrDefault(date, 0L)
                        ))
                        .toList();

        return new UrlAnalyticsResponse(
                shortUrl.getId(),
                shortUrl.getShortCode(),
                shortUrl.getClickCount(),
                days,
                dailyClicks
        );
    }
}
