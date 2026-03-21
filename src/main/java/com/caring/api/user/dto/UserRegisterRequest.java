package com.caring.api.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@AllArgsConstructor
@Getter
public class UserRegisterRequest {
    private final String username;
    private final String password;
    private final LocalDate birthDate;
    private final String name;
    private final String phoneNumber;
}
