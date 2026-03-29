package com.caring.domain.voice.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.user.entity.User;
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
        name = "monthly_emotion_report",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_monthly_report_user_month", columnNames = {"user_id", "report_month"})
        }
)
public class MonthlyEmotionReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "monthly_emotion_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_monthly_report_user")
    )
    private User user;

    @Column(name = "report_month", nullable = false, length = 7)
    private String reportMonth;

    @Column(name = "latest_voice_composite_id")
    private Long latestVoiceCompositeId;

    @Lob
    @Column(name = "report_message", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String reportMessage;

    public void update(Long latestVoiceCompositeId, String reportMessage) {
        this.latestVoiceCompositeId = latestVoiceCompositeId;
        this.reportMessage = reportMessage;
    }
}
