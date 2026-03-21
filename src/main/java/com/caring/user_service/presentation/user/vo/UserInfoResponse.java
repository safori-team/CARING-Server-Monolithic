package com.caring.user_service.presentation.user.vo;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class UserInfoResponse {
    private final String username;
    private final String name;
}
