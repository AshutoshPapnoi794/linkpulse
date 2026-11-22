package com.linkpulse.analytics.repository;

import java.time.LocalDate;

public interface DailyClickProjection {

    LocalDate getClickDate();

    Long getClicks();
}