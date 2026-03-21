package com.caring.user_service.domain.user.business.validator;

import com.caring.user_service.domain.user.entity.User;

public interface UserValidator {
    void checkPasswordEncode(User user, String password);
    void validateName(String name);
    void validatePassword(String password);
    void validateUsername(String username);
}
