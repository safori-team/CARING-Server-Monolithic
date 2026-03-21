package com.caring.user_service.presentation.user.service;

import com.caring.common.annotation.UseCase;
import com.caring.user_service.domain.user.business.adaptor.UserAdaptor;
import com.caring.user_service.domain.user.entity.User;
import com.caring.user_service.presentation.user.vo.UserInfoResponse;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetUserInfoUseCase {

    private final UserAdaptor userAdaptor;

    public UserInfoResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        return UserInfoResponse.builder()
                .name(user.getName())
                .username(user.getUsername())
                .build();
    }
}
