package com.onfilm.domain.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextNormalizer {

    public static String normalizeTag(String input) {
        String t = (input == null) ? "" : input.trim();
        t = t.replaceAll("^#+", "");   // 앞 # 제거 (장르에도 재사용 가능)
        t = t.replaceAll("\\s+", " "); // 다중 공백 -> 1개
        t = t.toLowerCase();           // 소문자
        return t;
    }
}
