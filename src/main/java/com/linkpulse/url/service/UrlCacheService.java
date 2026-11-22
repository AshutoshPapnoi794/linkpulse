package com.linkpulse.url.service;

import com.linkpulse.url.dto.CachedUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

@Service
public class UrlCacheService {
    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);

    private static final String KEY_PREFIX = "linkpulse:redirect:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Duration cacheTtl;

    public UrlCacheService(StringRedisTemplate redisTemplate, JsonMapper jsonMapper, @Value("${app.cache.redirect-ttl:5m}") Duration cacheTtl) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.cacheTtl = cacheTtl;
    }

    public Optional<CachedUrl> get(String shortCode){
        String key = createKey(shortCode);

        try {
            String cachedJson = redisTemplate.opsForValue().get(key);

            if (cachedJson==null){
                return Optional.empty();
            }

            try {
                CachedUrl cachedUrl = jsonMapper.readValue(cachedJson, CachedUrl.class);

                return Optional.of(cachedUrl);
            } catch (JacksonException e) {
                log.warn(
                        "Invalid Redis cache entry for short code {}. Evicting it.",
                        shortCode
                );
                redisTemplate.delete(key);
                return Optional.empty();
            }
        }catch (DataAccessException exception) {

            log.warn(
                    "Redis lookup failed for short code {}. Falling back to PostgreSQL.",
                    shortCode
            );

            return Optional.empty();
        }
    }

    public void put(CachedUrl cachedUrl, String shortCode){
        String key = createKey(shortCode);

        try {
            String json = jsonMapper.writeValueAsString(cachedUrl);

            redisTemplate.opsForValue().set(key,json,cacheTtl);
        }catch (JacksonException exception) {

            log.warn(
                    "Could not serialize URL cache entry for {}",
                    shortCode,
                    exception
            );

        } catch (DataAccessException exception) {

            log.warn(
                    "Redis cache write failed for {}. PostgreSQL remains authoritative.",
                    shortCode
            );
        }
    }

    public void evictAfterCommit(String shortCode){
        if (TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evict(shortCode);
                        }
                    }
            );
        }else{
            evict(shortCode);
        }
    }

    private void evict(String shortCode){
        try {
            redisTemplate.delete(createKey(shortCode));
        } catch (DataAccessException exception){
            log.warn(
                    "Redis cache eviction failed for {}. Entry will eventually expire.",
                    shortCode
            );
        }
    }

    private String createKey(String shortCode){
        return KEY_PREFIX + shortCode;
    }
}
