package com.caring.user_service.domain.user.business.domainService;

import com.caring.user_service.domain.user.entity.User;

import java.time.LocalDate;


public interface UserDomainService {

    User registerUser(String username, String password, String name, LocalDate birthday, String phoneNumber);


}
