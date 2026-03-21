package com.caring.api.auth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class SignInRequest {
    private final String username;
    private final String password;
}
