package com.linkpulse.url.service;

import com.linkpulse.analytics.service.ClickTrackingService;
import com.linkpulse.url.dto.CachedUrl;
import com.linkpulse.url.entity.ShortUrl;
import com.linkpulse.url.exception.UrlNotFoundException;
import com.linkpulse.url.exception.UrlUnavailableException;
import com.linkpulse.url.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

@Service
public class UrlRedirectService {
    private final UrlCacheService urlCacheService;
    private final ShortUrlRepository shortUrlRepository;
    private final ClickTrackingService clickTrackingService;

    public UrlRedirectService(UrlCacheService urlCacheService, ShortUrlRepository shortUrlRepository, ClickTrackingService clickTrackingService) {
        this.urlCacheService = urlCacheService;
        this.shortUrlRepository = shortUrlRepository;
        this.clickTrackingService = clickTrackingService;
    }

    public URI resolveAndRecordClick(String shortCode, String referrerHeader){
        CachedUrl cachedUrl = urlCacheService
                .get(shortCode)
                .orElseGet(()->loadFromDatabase(shortCode));

        if (!cachedUrl.active()){
            throw new UrlUnavailableException();
        }

        Instant now = Instant.now();

        if (cachedUrl.expiresAt() != null
                && !cachedUrl.expiresAt().isAfter(now)) {

            throw new UrlUnavailableException();
        }

        String referrer = extractReferrerHost(referrerHeader);

        clickTrackingService.recordClick(cachedUrl.id(),referrer);

        return URI.create(cachedUrl.originalUrl());
    }

    private CachedUrl loadFromDatabase(String shortCode){
        ShortUrl shortUrl = shortUrlRepository
                .findByShortCode(shortCode)
                .orElseThrow(UrlNotFoundException::new);

        CachedUrl cachedUrl = new CachedUrl(
                shortUrl.getId(),
                shortUrl.getOriginalUrl(),
                shortUrl.isActive(),
                shortUrl.getExpiresAt()
        );

        urlCacheService.put(cachedUrl,shortCode);

        return cachedUrl;
    }

    private String extractReferrerHost(String referrer){
        if (referrer==null || referrer.isBlank()){
            return null;
        }

        try {
            URI referrerUri = URI.create(referrer.trim());

            String scheme = referrerUri.getScheme();

            if (scheme==null){
                return null;
            }

            scheme = scheme.toLowerCase(Locale.ROOT);

            if (!scheme.equals("http")
                    && !scheme.equals("https")) {
                return null;
            }

            String host = referrerUri.getHost();

            if (host == null || host.isBlank()) {
                return null;
            }

            return host.toLowerCase(Locale.ROOT);
        }catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
