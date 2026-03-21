package com.caring.api.user.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.user.service.GetUserInfoUseCase;
import com.caring.api.user.service.SignUpUseCase;
import com.caring.api.user.dto.UserInfoResponse;
import com.caring.api.user.dto.UserRegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users")
public class UserApiController {

    public final SignUpUseCase signUpUseCase;
    public final GetUserInfoUseCase getUserInfoUseCase;

    @PostMapping("/sign-up")
    public ApiResponseDto<Long> signUp(@RequestBody UserRegisterRequest userRegisterRequest){
        return ApiResponseDto.onSuccess(signUpUseCase.execute(userRegisterRequest));
    }

    @GetMapping
    public ApiResponseDto<UserInfoResponse> getUserInfo(@UserCode String username) {
        return ApiResponseDto.onSuccess(getUserInfoUseCase.execute(username));
    }
}
