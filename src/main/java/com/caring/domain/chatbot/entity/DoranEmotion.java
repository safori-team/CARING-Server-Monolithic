package com.caring.domain.chatbot.entity;

import java.util.Locale;

public enum DoranEmotion {
    HAPPY, SAD, NEUTRAL, ANGRY, ANXIETY, SURPRISE;

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DoranEmotion fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("emotion code is null");
        }
        return DoranEmotion.valueOf(code.toUpperCase(Locale.ROOT));
    }
}
