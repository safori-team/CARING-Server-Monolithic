package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 세부 감정 레이블 저장 테이블.
 * Gemini 분석 결과의 fine-grained 감정 레이블(joy, anxiety 등)을
 * voice 단위로 집계하여 저장합니다.
 *
 * 프론트엔드 버블차트 활용:
 *   - 사용자 전체: GROUP BY label, COUNT(*) → 다이어리별 빈도
 *   - intensity_x1000: 강도 가중 버블 크기 계산용
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "voice_emotion_label",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_vel_voice_label", columnNames = {"voice_id", "label"})
        },
        indexes = {
                @Index(name = "idx_vel_voice", columnList = "voice_id"),
                @Index(name = "idx_vel_label", columnList = "label")
        }
)
public class VoiceEmotionLabel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_emotion_label_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vel_voice")
    )
    private Voice voice;

    /**
     * 대분류 카테고리 (neutral/happy/sad/angry/fear/surprise)
     */
    @Column(name = "category", length = 16, nullable = false)
    private String category;

    /**
     * 세부 감정 레이블 (joy, anxiety, frustration, awe 등 Gemini 반환 label)
     */
    @Column(name = "label", length = 32, nullable = false)
    private String label;

    /**
     * 해당 voice 내 모든 세그먼트에서의 intensity 합계 × 1000
     * (세그먼트가 여러 개일 경우 누적 합산)
     */
    @Column(name = "intensity_x1000", nullable = false)
    private Integer intensityX1000;
}
