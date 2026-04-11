package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.emotion.entity.EmotionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "voice_composite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_vcp_voice", columnNames = {"voice_id"})
        }
)
public class VoiceComposite extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_composite_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vc_voice2")
    )
    private Voice voice;

    // text sentiment (nullable)
    @Column(name = "text_score_bps")
    private Integer textScoreBps;

    @Column(name = "text_magnitude_x1000")
    private Integer textMagnitudeX1000;

    // EEG/feature weights (nullable)
    @Column(name = "alpha_bps")
    private Integer alphaBps;

    @Column(name = "beta_bps")
    private Integer betaBps;

    // composite outputs (not null)
    @Column(name = "valence_x1000", nullable = false)
    private Integer valenceX1000;

    @Column(name = "arousal_x1000", nullable = false)
    private Integer arousalX1000;

    @Column(name = "intensity_x1000", nullable = false)
    private Integer intensityX1000;

    // emotion distribution (SMALLINT UNSIGNED NOT NULL -> Integer 권장)
    @Column(name = "happy_bps", nullable = false)
    private Integer happyBps;

    @Column(name = "sad_bps", nullable = false)
    private Integer sadBps;

    @Column(name = "neutral_bps", nullable = false)
    private Integer neutralBps;

    @Column(name = "angry_bps", nullable = false)
    private Integer angryBps;

    @Column(name = "fear_bps", nullable = false)
    private Integer fearBps;

    @Column(name = "surprise_bps", nullable = false)
    private Integer surpriseBps;

    @Column(name = "top_emotion", length = 16)
    @Enumerated(EnumType.STRING)
    private EmotionType topEmotion;

    @Column(name = "top_emotion_confidence_bps")
    private Integer topEmotionConfidenceBps;
}
