package com.caring.user_service.presentation.security.controller;

import com.caring.common.annotation.UserCode;
import com.caring.common.dto.ApiResponseDto;
import com.caring.common.dto.vo.JwtToken;
import com.caring.user_service.presentation.security.service.SignInUseCase;
import com.caring.user_service.presentation.security.service.SignOutUseCase;
import com.caring.user_service.presentation.security.vo.SignInRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

@Tag(name = "[로그인(유저)]")
@Slf4j
@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
public class SecurityAccessApiController {

    private final SignInUseCase signInUseCase;
    private final SignOutUseCase signOutUseCase;


    @PostMapping("/sign-in")
    public ApiResponseDto<JwtToken> signIn(@RequestBody SignInRequest signInRequest) {
        return ApiResponseDto.onSuccess(signInUseCase.execute(signInRequest));
    }

    @DeleteMapping("/sign-out")
    public ApiResponseDto<String> signOut(@UserCode String refreshToken) {
        return ApiResponseDto.onSuccess(signOutUseCase.execute(refreshToken));
    }

}
