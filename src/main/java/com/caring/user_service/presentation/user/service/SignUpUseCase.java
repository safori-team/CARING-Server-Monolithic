package com.caring.user_service.presentation.user.service;

import com.caring.common.annotation.UseCase;
import com.caring.user_service.domain.user.business.domainService.UserDomainService;
import com.caring.user_service.domain.user.business.validator.UserValidator;
import com.caring.user_service.domain.user.entity.User;
import com.caring.user_service.presentation.user.vo.UserRegisterRequest;
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
