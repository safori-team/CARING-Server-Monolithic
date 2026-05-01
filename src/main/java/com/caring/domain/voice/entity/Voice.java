package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "voice")
public class Voice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_id")
    private Long id;

    private String voiceKey;
    private String voiceTitle;
    private int duration;
    private int sampleRate;
    private int bitRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus analysisStatus;

    private LocalDateTime analysisCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public void markAnalysisPending() {
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void markAnalysisCompleted() {
        this.analysisStatus = AnalysisStatus.COMPLETED;
        this.analysisCompletedAt = LocalDateTime.now();
    }

    public void markAnalysisFailed() {
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    public enum AnalysisStatus {
        PENDING, COMPLETED, FAILED
    }
}
