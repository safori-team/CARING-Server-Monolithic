package com.caring.domain.user.adaptor;

import com.caring.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public interface UserAdaptor {

    User queryUserById(Long userId);

    List<User> queryAll();

    User queryUserByUsername(String username);

    User queryUserByUserUuid(String userUuid);

    List<User> queryByUserUuidList(List<String> userUuidList);


    Stream<String> streamFcmTokensByProvince(String province);

    Stream<String> streamFcmTokensByProvinceAndCityDistrict(String province, String cityDistrict);
}
