package com.caring.user_service.domain.user.repository;

import com.caring.user_service.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUserUuid(String userUuid);

    List<User> findByUserUuidIn(List<String> userUuids);

    Optional<User> findByNameAndBirthDateAndPhoneNumber(String name, LocalDate birthDate, String phoneNumber);

    @Query("SELECT u.fcmToken.token FROM User u WHERE u.province = :province AND u.fcmToken.token IS NOT NULL")
    Stream<String> findFcmTokensByProvince(String province);

    @Query("SELECT u.fcmToken.token FROM User u WHERE u.province = :province AND u.cityDistrict = :cityDistrict AND u.fcmToken.token IS NOT NULL")
    Stream<String> findFcmTokensProvinceAndCityDistrict(String province, String cityDistrict);
}
