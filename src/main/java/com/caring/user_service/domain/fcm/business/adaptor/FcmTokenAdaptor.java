package com.caring.user_service.domain.fcm.business.adaptor;

import com.caring.user_service.domain.fcm.entity.FcmToken;

public interface FcmTokenAdaptor {

    FcmToken queryFcmTokenByUserId(Long userId);
}
