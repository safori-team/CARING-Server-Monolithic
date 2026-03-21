package com.caring.common.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RequestLogin {
    private final String memberCode;
    private final String password;
}
