package com.caring.user_service.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

public class TokenUtil {

    public static String getToken(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
