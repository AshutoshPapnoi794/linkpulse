package com.linkpulse.analytics.service;

import com.linkpulse.analytics.entity.ClickEvent;
import com.linkpulse.analytics.repository.ClickEventRepository;
import com.linkpulse.url.entity.ShortUrl;
import com.linkpulse.url.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickTrackingService {
    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;

    public ClickTrackingService(ClickEventRepository clickEventRepository, ShortUrlRepository shortUrlRepository) {
        this.clickEventRepository = clickEventRepository;
        this.shortUrlRepository = shortUrlRepository;
    }

    @Transactional
    public void recordClick(Long urlId, String referrer){
        int updatedRows = shortUrlRepository.incrementClickCount(urlId);

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Could not update URL click counter"
            );
        }

        ShortUrl shortUrl = shortUrlRepository.getReferenceById(urlId);

        ClickEvent event = new ClickEvent(
                shortUrl,
                referrer
        );

        clickEventRepository.save(event);
    }
}
