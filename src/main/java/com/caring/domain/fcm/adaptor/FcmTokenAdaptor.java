package com.caring.domain.fcm.adaptor;

import com.caring.domain.fcm.entity.FcmToken;

public interface FcmTokenAdaptor {

    FcmToken queryFcmTokenByUserId(Long userId);
}
