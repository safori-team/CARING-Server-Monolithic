package com.caring.api.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class UserInfoResponse {
    private final String username;
    private final String name;
}
