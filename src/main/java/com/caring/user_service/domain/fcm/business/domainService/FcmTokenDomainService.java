package com.caring.user_service.domain.fcm.business.domainService;

import com.caring.user_service.domain.fcm.entity.FcmToken;
import com.caring.user_service.domain.user.entity.User;

public interface FcmTokenDomainService {

    Long saveFcmToken(String token, User user);

    void updateFcmToken(FcmToken fcmToken, String token);
}
