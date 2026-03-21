package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "voice_analyze",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_va_voice", columnNames = {"voice_id"})
        }
)
public class VoiceAnalyze extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_analyze_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_va_voice")
    )
    private Voice voice;

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
    private String topEmotion;

    @Column(name = "top_confidence_bps")
    private Integer topConfidenceBps;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

}
