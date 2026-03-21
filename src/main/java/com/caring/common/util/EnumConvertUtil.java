package com.caring.common.util;

import com.caring.common.interfaces.KeyedEnum;

import java.util.EnumSet;

/**
 * Enum 변환 유틸리티
 * Key 값으로 Enum을 찾는 공통 로직 제공
 */
public class EnumConvertUtil {

    /**
     * Key 값으로 Enum을 찾아 반환
     * @param enumClass Enum 클래스
     * @param key 찾을 Key 값
     * @param <E> Enum 타입
     * @return 일치하는 Enum 값 또는 null
     */
    public static <E extends Enum<E> & KeyedEnum> E fromKey(Class<E> enumClass, String key) {
        if (key == null) {
            return null;
        }
        return EnumSet.allOf(enumClass)
                .stream()
                .filter(e -> e.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    /**
     * Key 값으로 Enum을 찾아 반환 (없으면 예외 발생)
     * @param enumClass Enum 클래스
     * @param key 찾을 Key 값
     * @param <E> Enum 타입
     * @return 일치하는 Enum 값
     * @throws IllegalArgumentException key에 해당하는 Enum이 없을 경우
     */
    public static <E extends Enum<E> & KeyedEnum> E fromKeyOrThrow(Class<E> enumClass, String key) {
        E result = fromKey(enumClass, key);
        if (result == null) {
            throw new IllegalArgumentException(
                    String.format("No enum constant for key '%s' in %s", key, enumClass.getSimpleName())
            );
        }
        return result;
    }
}

