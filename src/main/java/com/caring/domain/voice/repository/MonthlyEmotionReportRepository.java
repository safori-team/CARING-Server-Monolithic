package com.caring.domain.voice.repository;

import com.caring.domain.voice.entity.MonthlyEmotionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyEmotionReportRepository extends JpaRepository<MonthlyEmotionReport, Long> {

    Optional<MonthlyEmotionReport> findByUser_IdAndReportMonth(Long userId, String reportMonth);
}
