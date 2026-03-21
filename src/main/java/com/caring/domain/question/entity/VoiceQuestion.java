package com.caring.domain.question.entity;

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
@Table(name = "voice_question")
public class VoiceQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vq_voice")
    )
    private Voice voice;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_category", nullable = false, length = 50)
    private QuestionCategory questionCategory;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;
}
