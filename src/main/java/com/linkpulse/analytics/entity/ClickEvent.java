
package com.linkpulse.analytics.entity;

import com.linkpulse.url.entity.ShortUrl;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "short_url_id",
            nullable = false
    )
    private ShortUrl shortUrl;

    @Column(
            name = "clicked_at",
            nullable = false,
            updatable = false
    )
    private Instant clickedAt;

    @Column(length = 2048)
    private String referrer;

    protected ClickEvent() {
        // Required by JPA
    }

    public ClickEvent(
            ShortUrl shortUrl,
            String referrer
    ) {
        this.shortUrl = shortUrl;
        this.referrer = referrer;
        this.clickedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ShortUrl getShortUrl() {
        return shortUrl;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getReferrer() {
        return referrer;
    }
}
