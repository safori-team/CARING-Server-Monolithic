package com.caring.domain.job.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.voice.entity.Voice;
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
@Table(name = "voice_job_process")
public class VoiceJobProcess extends BaseTimeEntity {
    @Id
    @Column(name = "voice_id", nullable = false)
    private Long voiceId;

    /**
     * PK=FK 매핑
     * @MapsId를 통해 칼럼을 중복시키지 않으면서 JPA를 통해 voice를 호출 가능
     */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vjp_voice")
    )
    private Voice voice;

    @Column(name = "text_done", nullable = false)
    private boolean textDone = false;

    @Column(name = "audio_done", nullable = false)
    private boolean audioDone = false;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;


}
