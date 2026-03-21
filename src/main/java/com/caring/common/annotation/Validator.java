package com.caring.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Validator 컴포넌트를 표시하는 어노테이션
 * 비즈니스 규칙 검증을 담당하는 클래스에 사용
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Validator {
    @AliasFor(annotation = Component.class)
    String value() default "";
}

