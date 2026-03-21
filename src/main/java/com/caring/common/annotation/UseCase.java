package com.caring.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * UseCase 레이어 컴포넌트를 표시하는 어노테이션
 * 비즈니스 로직을 처리하는 서비스 클래스에 사용
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface UseCase {
    @AliasFor(annotation = Component.class)
    String value() default "";
}

