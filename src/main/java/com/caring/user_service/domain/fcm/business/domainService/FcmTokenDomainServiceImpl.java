package com.caring.user_service.domain.fcm.business.domainService;

import com.caring.common.annotation.DomainService;
import com.caring.user_service.domain.fcm.entity.FcmToken;
import com.caring.user_service.domain.fcm.repository.FcmTokenRepository;
import com.caring.user_service.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class FcmTokenDomainServiceImpl implements FcmTokenDomainService {

    private final FcmTokenRepository fcmTokenRepository;
    @Override
    public Long saveFcmToken(String token, User user) {
        FcmToken fcmToken = FcmToken.builder()
                .token(token)
                .user(user)
                .build();
        return fcmTokenRepository.save(fcmToken).getId();
    }

    @Override
    public void updateFcmToken(FcmToken fcmToken, String token) {
        fcmToken.updateToken(token);
    }
}
