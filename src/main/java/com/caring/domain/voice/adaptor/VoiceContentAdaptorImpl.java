package com.caring.domain.voice.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.repository.VoiceContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Adaptor
@RequiredArgsConstructor
public class VoiceContentAdaptorImpl implements VoiceContentAdaptor {

    private final VoiceContentRepository voiceContentRepository;

    @Override
    @Transactional
    public VoiceContent save(VoiceContent voiceContent) {
        return voiceContentRepository.save(voiceContent);
    }
}
