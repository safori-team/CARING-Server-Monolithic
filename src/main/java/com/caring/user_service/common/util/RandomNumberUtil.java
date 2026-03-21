package com.caring.user_service.common.util;

import java.util.Random;

public class RandomNumberUtil {

    private final static int MEMBER_CODE_LENGTH = 7;

    public static String generateRandomNumber(int passwordLength) {
        int index = 0;
        char[] charSet = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
        };	//배열안의 문자 숫자는 원하는대로

        StringBuffer password = new StringBuffer();
        Random random = new Random();

        for (int i = 0; i < passwordLength ; i++) {
            double rd = random.nextDouble();
            index = (int) (charSet.length * rd);

            password.append(charSet[index]);
        }
        return password.toString();
    }

    public static String generateRandomMemberCode(String prefix) {
        String number = generateRandomNumber(MEMBER_CODE_LENGTH);
        return prefix.concat(number);
    }
}
