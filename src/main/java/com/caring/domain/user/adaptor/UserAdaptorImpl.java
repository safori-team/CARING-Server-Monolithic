package com.caring.domain.user.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.user.validator.UserValidator;
import com.caring.domain.user.entity.User;
import com.caring.domain.user.exception.UserHandler;
import com.caring.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static com.caring.domain.user.exception.UserHandler.NOT_FOUND;

@Adaptor
@RequiredArgsConstructor
public class UserAdaptorImpl implements UserAdaptor{

    private final UserRepository userRepository;

    @Override
    public User queryUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public List<User> queryAll() {
        return userRepository.findAll();
    }

    @Override
    public User queryUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public User queryUserByUserUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public List<User> queryByUserUuidList(List<String> userUuidList) {
        return userRepository.findByUserUuidIn(userUuidList);
    }

    @Override
    public Stream<String> streamFcmTokensByProvince(String province) {
        return userRepository.findFcmTokensByProvince(province);
    }

    @Override
    public Stream<String> streamFcmTokensByProvinceAndCityDistrict(String province, String cityDistrict) {
        return userRepository.findFcmTokensProvinceAndCityDistrict(province, cityDistrict);
    }
}
