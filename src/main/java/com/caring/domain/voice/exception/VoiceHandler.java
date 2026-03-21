package com.caring.domain.voice.exception;

import com.caring.common.exception.BaseErrorCode;
import com.caring.common.exception.ErrorStatus;
import com.caring.common.exception.GeneralException;

public class VoiceHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new VoiceHandler(ErrorStatus.VOICE_NOT_FOUND);
    public static final GeneralException NO_PERMISSION =
            new VoiceHandler(ErrorStatus.VOICE_NO_PERMISSION);

    public VoiceHandler(BaseErrorCode code) {
        super(code);
    }
}
