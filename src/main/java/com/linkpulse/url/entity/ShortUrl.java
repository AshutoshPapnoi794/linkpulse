package com.linkpulse.url.entity;

import com.linkpulse.auth.entity.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            name = "original_url",
            nullable = false,
            columnDefinition = "text"
    )
    private String originalUrl;

    @Column(
            name = "short_code",
            nullable = false,
            length = 16
    )
    private String shortCode;

    @Column(
            name = "is_active",
            nullable = false
    )
    private boolean active;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(
            name = "click_count",
            nullable = false
    )
    private long clickCount = 0;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected ShortUrl(){}

    public ShortUrl(User user, String originalUrl, String shortCode, Instant expiresAt) {
        this.user = user;
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.active = true;
        this.expiresAt = expiresAt;
        this.clickCount = 0;
    }

    @PrePersist
    public void onCreate(){
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate(){
        this.updatedAt = Instant.now();
    }

    public void updateExpiration(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void deactivate(){
        this.active=false;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
