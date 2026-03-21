package com.caring.common.interfaces;

/**
 * 데이터 조회 함수형 인터페이스
 * 람다 표현식으로 데이터 조회 로직 전달 시 사용
 */
@FunctionalInterface
public interface QueryFunction<T, R> {
    R apply(T input);
}

