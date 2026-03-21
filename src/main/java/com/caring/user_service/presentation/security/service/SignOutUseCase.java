package com.caring.user_service.presentation.security.service;

import com.caring.common.annotation.UseCase;
import com.caring.user_service.presentation.security.service.user.UserTokenService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignOutUseCase {

    private final UserTokenService userTokenService;

    public String execute(String refreshToken) {
        //TODO 리턴값 처리 -> boolean return이 true로 고정
        return userTokenService.logout(refreshToken) ? "로그아웃에 성공하였습니다" : "잘못된 리프레시 토큰입니다";
    }
}
