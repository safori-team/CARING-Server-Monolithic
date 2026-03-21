package com.caring.api.user.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.user.service.UserDomainService;
import com.caring.domain.user.validator.UserValidator;
import com.caring.domain.user.entity.User;
import com.caring.api.user.dto.UserRegisterRequest;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignUpUseCase {

    private final UserDomainService userDomainService;
    private final UserValidator userValidator;

    public Long execute(UserRegisterRequest userRegisterRequest) {
        userValidator.validateUsername(userRegisterRequest.getUsername());
        userValidator.validatePassword(userRegisterRequest.getPassword());
        User user = userDomainService.registerUser(userRegisterRequest.getUsername(),
                userRegisterRequest.getPassword(),
                userRegisterRequest.getName(),
                userRegisterRequest.getBirthDate(),
                userRegisterRequest.getPhoneNumber());
        return user.getId();
    }
}
