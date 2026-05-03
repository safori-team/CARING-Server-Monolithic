package com.caring.domain.voice.adaptor;

import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceEmotionLabel;

import java.util.List;

public interface VoiceEmotionLabelAdaptor {

    List<VoiceEmotionLabel> saveAll(List<VoiceEmotionLabel> labels);

    List<VoiceEmotionLabel> findByVoiceId(Long voiceId);

    /**
     * 특정 월의 label별 집계 데이터 반환 (버블차트용).
     * Object[] = { label, category, diaryCount(Long), avgIntensity(Double) }
     */
    List<Object[]> findMonthlyLabelStats(String username, int year, int month);

    /**
     * 특정 월에 특정 label이 기록된 Voice 목록 (일기 리스트용).
     */
    List<Voice> findVoicesByLabel(String username, String label, int year, int month);
}
