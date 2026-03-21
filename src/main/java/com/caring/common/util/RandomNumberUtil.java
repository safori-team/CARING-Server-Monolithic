package com.caring.common.util;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 랜덤 숫자 생성 유틸리티
 */
public class RandomNumberUtil {

    private static final Random RANDOM = new SecureRandom();

    /**
     * 지정된 길이의 랜덤 숫자 문자열 생성
     * @param length 생성할 숫자 문자열 길이
     * @return 랜덤 숫자 문자열
     */
    public static String generateRandomNumber(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 지정된 범위 내의 랜덤 정수 생성
     * @param min 최소값 (포함)
     * @param max 최대값 (포함)
     * @return 랜덤 정수
     */
    public static int generateRandomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }
}

