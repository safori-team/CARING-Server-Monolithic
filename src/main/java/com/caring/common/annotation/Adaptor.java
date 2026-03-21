package com.caring.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Adaptor 레이어 컴포넌트를 표시하는 어노테이션
 * 도메인과 외부 시스템 간의 통신을 담당하는 클래스에 사용
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Adaptor {
    @AliasFor(annotation = Component.class)
    String value() default "";
}

