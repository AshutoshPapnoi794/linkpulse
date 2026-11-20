package com.linkpulse.url.service;

import com.linkpulse.auth.entity.User;
import com.linkpulse.auth.repository.UserRepository;
import com.linkpulse.url.entity.ShortUrl;
import com.linkpulse.url.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ShortUrlCreationAttemptService {
    private final UserRepository userRepository;
    private final ShortUrlRepository shortUrlRepository;

    public ShortUrlCreationAttemptService(UserRepository userRepository, ShortUrlRepository shortUrlRepository) {
        this.userRepository = userRepository;
        this.shortUrlRepository = shortUrlRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShortUrl create(Long userId, String originalUrl, String shortCode, Instant expiresAt){

        User user = userRepository.getReferenceById(userId);

        ShortUrl shortUrl = new ShortUrl(
                user,
                originalUrl,
                shortCode,
                expiresAt
        );

        return shortUrlRepository.saveAndFlush(shortUrl);
    }
}
