package com.linkpulse.url.service;

import com.linkpulse.url.dto.CreateUrlRequest;
import com.linkpulse.url.dto.PageResponse;
import com.linkpulse.url.dto.ShortUrlResponse;
import com.linkpulse.url.dto.UpdateUrlRequest;
import com.linkpulse.url.entity.ShortUrl;
import com.linkpulse.url.exception.AliasAlreadyTakenException;
import com.linkpulse.url.exception.ReservedAliasException;
import com.linkpulse.url.exception.ShortCodeGenerationException;
import com.linkpulse.url.exception.UrlNotFoundException;
import com.linkpulse.url.repository.ShortUrlRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class UrlService {
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api",
            "actuator",
            "error",
            "swagger",
            "swagger-ui",
            "docs",
            "health"
    );

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final OriginalUrlValidator originalUrlValidator;
    private final ShortUrlCreationAttemptService creationAttemptService;
    private final UrlCacheService urlCacheService;
    private final String baseUrl;

    public UrlService(ShortUrlRepository shortUrlRepository, ShortCodeGenerator shortCodeGenerator, OriginalUrlValidator originalUrlValidator, ShortUrlCreationAttemptService creationAttemptService, UrlCacheService urlCacheService, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.originalUrlValidator = originalUrlValidator;
        this.creationAttemptService = creationAttemptService;
        this.urlCacheService = urlCacheService;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public ShortUrlResponse createUrl(Long userId, CreateUrlRequest request){
        String originalUrl = originalUrlValidator.validateAndNormalize(request.originalUrl());

        Instant expiresAt = request.expiresAt();

        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Expiration must be in the future"
            );
        }

        if (request.customAlias()!=null){
            String alias = request.customAlias().toLowerCase(Locale.ROOT);

            if (RESERVED_ALIASES.contains(alias)){
                throw new ReservedAliasException();
            }

            if (shortUrlRepository.existsByShortCode(alias)) {
                throw new AliasAlreadyTakenException();
            }

            try {
                ShortUrl shortUrl = creationAttemptService.create(
                        userId,
                        originalUrl,
                        alias,
                        expiresAt
                );

                return toResponse(shortUrl);
            }catch (DataIntegrityViolationException exception) {

                if (isShortCodeUniqueViolation(exception)) {
                    throw new AliasAlreadyTakenException();
                }

                throw exception;
            }
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS ; attempt++) {
            String code = shortCodeGenerator.generateCode();

            if (RESERVED_ALIASES.contains(
                    code.toLowerCase(Locale.ROOT))) {
                continue;
            }

            if (shortUrlRepository.existsByShortCode(code)) {
                continue;
            }

            try {

                ShortUrl shortUrl = creationAttemptService.create(
                        userId,
                        originalUrl,
                        code,
                        expiresAt
                );

                return toResponse(shortUrl);

            } catch (DataIntegrityViolationException exception) {

                if (!isShortCodeUniqueViolation(exception)) {
                    throw exception;
                }
            }
        }

        throw new ShortCodeGenerationException();
    }

    @Transactional(readOnly = true)
    public PageResponse<ShortUrlResponse> getMyUrls(Long userId, int page, int size){
        if (page<0 || size<1 || size>MAX_PAGE_SIZE){
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        Page<ShortUrl> result = shortUrlRepository.findAllByUser_idOrderByCreatedAtDesc(userId, PageRequest.of(page,size));

        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ShortUrlResponse getUrl(Long userId, Long urlId){
        ShortUrl shortUrl = shortUrlRepository
                .findByIdAndUser_Id(urlId,userId)
                .orElseThrow(UrlNotFoundException::new);

        return toResponse(shortUrl);
    }

    @Transactional
    public ShortUrlResponse updateExpiration(Long userId, Long urlId, UpdateUrlRequest request){
        if (request.expiresAt()==null || !request.expiresAt().isAfter(Instant.now())){
            throw new IllegalArgumentException(
                    "Expiration must be in the future"
            );
        }

        ShortUrl shortUrl = shortUrlRepository
                .findByIdAndUser_Id(urlId,userId)
                .orElseThrow(UrlNotFoundException::new);

        shortUrl.updateExpiration(request.expiresAt());
        ShortUrl saved = shortUrlRepository.save(shortUrl);
        urlCacheService.evictAfterCommit(saved.getShortCode());
        return toResponse(saved);
    }

    @Transactional
    public void deactivateUrl(Long userId, Long urlId){
        ShortUrl shortUrl = shortUrlRepository
                .findByIdAndUser_Id(urlId,userId)
                .orElseThrow(UrlNotFoundException::new);

        shortUrl.deactivate();
        shortUrlRepository.save(shortUrl);
        urlCacheService.evictAfterCommit(shortUrl.getShortCode());
    }

    private ShortUrlResponse toResponse(ShortUrl url) {

        return new ShortUrlResponse(
                url.getId(),
                url.getOriginalUrl(),
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.isActive(),
                url.getExpiresAt(),
                url.getClickCount(),
                url.getCreatedAt(),
                url.getUpdatedAt()
        );
    }

    private boolean isShortCodeUniqueViolation(Throwable exception){
        Throwable current = exception;

        while (current!=null){
            if (current instanceof ConstraintViolationException violation){
                if ("uk_short_urls_short_code".equals(violation.getConstraintName())){
                    return true;
                }
            }

            if (current instanceof SQLException sqlException){
                SQLException sql = sqlException;

                while (sql!=null){
                    if ("23505".equals(sql.getSQLState())
                            && sql.getMessage() != null
                            && sql.getMessage().contains(
                            "uk_short_urls_short_code")) {

                        return true;
                    }
                    sql = sql.getNextException();
                }
            }

            current = current.getCause();
        }

        return false;
    }
}
