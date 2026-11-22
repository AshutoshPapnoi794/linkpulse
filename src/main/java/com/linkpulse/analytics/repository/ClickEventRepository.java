package com.linkpulse.analytics.repository;

import com.linkpulse.analytics.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent,Long> {

    @Query(value = """
    SELECT
        CAST(clicked_at AT TIME ZONE 'UTC' AS DATE)
            AS "clickDate",
        COUNT(*) AS clicks
    FROM click_events
    WHERE short_url_id = :urlId
      AND clicked_at >= :from
      AND clicked_at < :to
    GROUP BY
        CAST(clicked_at AT TIME ZONE 'UTC' AS DATE)
    ORDER BY "clickDate"
    """, nativeQuery = true)
    List<DailyClickProjection> findDailyClicks(
            @Param("urlId") Long urlId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
