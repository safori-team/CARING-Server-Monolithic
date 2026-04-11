package com.caring.common.annotation;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * Injects the authenticated user's username from Spring Security principal.
 */
@Hidden
@AuthenticationPrincipal(expression = "username")
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserCode {
}
