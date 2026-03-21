package com.caring.common.interfaces;

import com.caring.common.dto.JwtToken;
import org.springframework.security.core.Authentication;

public interface TokenService {
    JwtToken login(String username, String password);

    JwtToken reissueToken(String refreshToken);

    JwtToken generateToken(Authentication authentication);

    Authentication getAuthentication(String accessToken);

    boolean logout(String refreshToken);

    boolean existsRefreshToken(String refreshToken);
}
