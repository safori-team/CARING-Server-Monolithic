package com.caring.user_service.presentation.security.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.dto.vo.JwtToken;
import com.caring.user_service.presentation.security.service.user.UserTokenService;
import com.caring.user_service.presentation.security.vo.SignInRequest;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignInUseCase {

    private final UserTokenService userTokenService;

    public JwtToken execute(SignInRequest signInRequest) {
        return userTokenService.login(signInRequest.getUsername(),  signInRequest.getPassword());
    }
}
