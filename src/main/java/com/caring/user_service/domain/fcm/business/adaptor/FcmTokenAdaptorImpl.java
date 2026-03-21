package com.caring.user_service.domain.fcm.business.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.user_service.domain.fcm.entity.FcmToken;
import com.caring.user_service.domain.fcm.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;

@Adaptor
@RequiredArgsConstructor
public class FcmTokenAdaptorImpl implements FcmTokenAdaptor {

    private final FcmTokenRepository fcmTokenRepository;
    @Override
    public FcmToken queryFcmTokenByUserId(Long userId) {
        return fcmTokenRepository.findFcmTokenByUserId(userId)
                .orElseThrow(() -> new RuntimeException("not found token"));
    }
}
