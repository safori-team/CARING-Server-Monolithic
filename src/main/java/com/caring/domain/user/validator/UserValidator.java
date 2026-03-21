package com.caring.domain.user.validator;

import com.caring.domain.user.entity.User;

public interface UserValidator {
    void checkPasswordEncode(User user, String password);
    void validateName(String name);
    void validatePassword(String password);
    void validateUsername(String username);
}
