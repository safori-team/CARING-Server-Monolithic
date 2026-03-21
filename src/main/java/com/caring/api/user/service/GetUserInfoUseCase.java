package com.caring.api.user.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.api.user.dto.UserInfoResponse;
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
