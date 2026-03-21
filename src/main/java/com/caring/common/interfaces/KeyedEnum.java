package com.caring.common.interfaces;

/**
 * Key 값을 가지는 Enum을 위한 공통 인터페이스
 * DB 저장 시 key 값으로 변환하여 저장
 */
public interface KeyedEnum {
    String getKey();
}

