package com.caring.common.annotation;

import io.swagger.v3.oas.annotations.Hidden;

import java.lang.annotation.*;

/**
 * User Code를 주입받기 위한 어노테이션
 * 컨트롤러 메서드 파라미터에 사용
 */
@Hidden
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserCode {
}

