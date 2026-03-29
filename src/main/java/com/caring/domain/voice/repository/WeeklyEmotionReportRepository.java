package com.caring.domain.voice.repository;

import com.caring.domain.voice.entity.WeeklyEmotionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyEmotionReportRepository extends JpaRepository<WeeklyEmotionReport, Long> {

    Optional<WeeklyEmotionReport> findByUser_IdAndReportMonthAndReportWeek(Long userId, String reportMonth, Integer reportWeek);
}
