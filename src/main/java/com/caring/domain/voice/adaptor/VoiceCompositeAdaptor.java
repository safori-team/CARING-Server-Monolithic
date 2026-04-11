package com.caring.domain.voice.adaptor;

import com.caring.domain.voice.entity.VoiceComposite;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceCompositeAdaptor {

    List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end);
    List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds);
}
