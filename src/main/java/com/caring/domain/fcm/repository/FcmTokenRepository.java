package com.caring.domain.fcm.repository;

import com.caring.domain.fcm.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findFcmTokenByUserId(Long userId);
}
