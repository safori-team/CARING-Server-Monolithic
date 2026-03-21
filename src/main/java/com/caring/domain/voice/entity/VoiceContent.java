package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
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
        name = "voice_content",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_vc_voice", columnNames = {"voice_id"})
        }
)
public class VoiceContent extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_content_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vc_voice")
    )
    private Voice voice;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "score_bps")
    private Short scoreBps;

    @Column(name = "magnitude_x1000")
    private Integer magnitudeX1000;

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "confidence_bps")
    private Short confidenceBps;

}
