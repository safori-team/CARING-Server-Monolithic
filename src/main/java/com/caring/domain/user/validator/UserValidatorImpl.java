package com.caring.domain.user.validator;

import com.caring.common.annotation.Validator;
import com.caring.domain.user.entity.User;
import com.caring.domain.user.exception.UserHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import static com.caring.domain.user.exception.UserHandler.PASSWORD_NOT_MATCH;

@Validator
@RequiredArgsConstructor
public class UserValidatorImpl implements UserValidator{
    private final PasswordEncoder passwordEncoder;

    /**
     * only use in filter, so need to throw filterException
     * @param user
     * @param password
     */
    @Override
    public void checkPasswordEncode(User user, String password) {
        if(!passwordEncoder.matches(password, user.getPassword())) throw PASSWORD_NOT_MATCH;
    }

    @Override
    public void validateName(String name) {
        if(name == null) {
            throw new IllegalArgumentException("이름은 null일 수 없습니다");
        }
        if(!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("이름은 빈 문자열일 수 없습니다");
        }
    }

    @Override
    public void validatePassword(String password) {
        if(password == null) {
            throw new IllegalArgumentException("비밀번호는 null일 수 없습니다");
        }
        if(!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("비밀번호는 빈 문자열일 수 없습니다");
        }
    }

    @Override
    public void validateUsername(String username) {
        if(username == null) {
            throw new IllegalArgumentException("멤버코드는 null일 수 없습니다");
        }
        if(!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("멤버코드는 빈 문자열일 수 없습니다");
        }
    }

}
