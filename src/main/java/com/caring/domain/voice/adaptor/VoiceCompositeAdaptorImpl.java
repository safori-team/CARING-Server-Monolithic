package com.caring.domain.voice.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.repository.VoiceCompositeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VoiceCompositeAdaptorImpl implements VoiceCompositeAdaptor {

    private final VoiceCompositeRepository voiceCompositeRepository;

    @Override
    public List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end) {
        return voiceCompositeRepository.findByVoice_User_UsernameAndCreatedDateBetween(username, start, end);
    }

    @Override
    public List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds) {
        if (voiceIds.isEmpty()) {
            return List.of();
        }
        return voiceCompositeRepository.findByVoice_IdIn(voiceIds);
    }
}
