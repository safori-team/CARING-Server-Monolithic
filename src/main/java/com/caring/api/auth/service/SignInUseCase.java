package com.caring.api.auth.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.dto.JwtToken;
import com.caring.security.service.UserTokenService;
import com.caring.api.auth.dto.SignInRequest;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignInUseCase {

    private final UserTokenService userTokenService;

    public JwtToken execute(SignInRequest signInRequest) {
        return userTokenService.login(signInRequest.getUsername(),  signInRequest.getPassword());
    }
}
