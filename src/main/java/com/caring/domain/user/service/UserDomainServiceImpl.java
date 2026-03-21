package com.caring.domain.user.service;

import com.caring.common.annotation.DomainService;
import com.caring.domain.user.validator.UserValidator;
import com.caring.domain.user.entity.Role;
import com.caring.domain.user.entity.User;
import com.caring.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static com.caring.common.consts.StaticVariable.USER_MEMBER_CODE_PRESET;
import static com.caring.common.util.RandomNumberUtil.generateRandomMemberCode;

@Transactional
@DomainService
@RequiredArgsConstructor
public class UserDomainServiceImpl implements UserDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    public User registerUser(String username,
                             String password,
                             String name,
                             LocalDate birthday,
                             String phoneNumber) {
        //TODO add address
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .name(name)
                .role(Role.USER)
                .birthDate(birthday)
                .phoneNumber(phoneNumber)
                .userUuid(UUID.randomUUID().toString())
                .build();
        return userRepository.save(user);
    }
}