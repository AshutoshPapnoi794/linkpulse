package com.linkpulse.url.repository;

import com.linkpulse.url.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    @Transactional(readOnly = true)
    boolean existsByShortCode(String shortCode);

    @Transactional(readOnly = true)
    Optional<ShortUrl> findByShortCode(String shortCode);

    Optional<ShortUrl> findByIdAndUser_Id(Long id, Long userId);

    Page<ShortUrl> findAllByUser_idOrderByCreatedAtDesc(Long userId, Pageable pageable);


    @Modifying(flushAutomatically = true)
    @Query("""
    UPDATE ShortUrl url
    SET url.clickCount = url.clickCount + 1
    WHERE url.id = :urlId
""")
    int incrementClickCount(
            @Param("urlId") Long urlId
    );

}
