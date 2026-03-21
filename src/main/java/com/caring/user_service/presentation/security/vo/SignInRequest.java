package com.caring.user_service.presentation.security.vo;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class SignInRequest {
    private final String username;
    private final String password;
}
