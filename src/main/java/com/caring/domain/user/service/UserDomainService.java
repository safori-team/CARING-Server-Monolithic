package com.caring.domain.user.service;

import com.caring.domain.user.entity.User;

import java.time.LocalDate;


public interface UserDomainService {

    User registerUser(String username, String password, String name, LocalDate birthday, String phoneNumber);


}
