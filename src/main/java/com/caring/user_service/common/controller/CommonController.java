package com.caring.user_service.common.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class CommonController {

    private final Environment env;

    @GetMapping("/health_check")
    public String status() {
        return String.format("It's working in UserService"
                + ", port(local.server.port)" + env.getProperty("local.server.port")
                + ", port(server.port)" + env.getProperty("server.port")
                + ", token.secret-user" + env.getProperty("token.secret-user")
                + ", token.secret-manager" + env.getProperty("token.secret-manager")
                + ", token expiration time" + env.getProperty("token.expiration_time"));
    }

    @GetMapping("/welcome")
    public String welcome() {
        return env.getProperty("greeting.message");
    }
}
