package com.linkpulse.url.repository;

import com.linkpulse.url.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    @Transactional(readOnly = true)
    boolean existsByShortCode(String shortCode);

    @Transactional(readOnly = true)
    Optional<ShortUrl> findByShortCode(String shortCode);

    Optional<ShortUrl> findByIdAndUser_Id(Long id, Long userId);

    Page<ShortUrl> findAllByUser_idOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
