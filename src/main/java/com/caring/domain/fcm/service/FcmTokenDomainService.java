package com.caring.domain.fcm.service;

import com.caring.domain.fcm.entity.FcmToken;
import com.caring.domain.user.entity.User;

public interface FcmTokenDomainService {

    Long saveFcmToken(String token, User user);

    void updateFcmToken(FcmToken fcmToken, String token);
}
