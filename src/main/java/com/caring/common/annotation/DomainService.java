package com.caring.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Domain Service 레이어 컴포넌트를 표시하는 어노테이션
 * 도메인 비즈니스 로직을 담당하는 클래스에 사용
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {
    @AliasFor(annotation = Component.class)
    String value() default "";
}

