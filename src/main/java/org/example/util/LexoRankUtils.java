package org.example.util;

import org.springframework.stereotype.Component;

@Component
public class LexoRankUtils {

    private static final int MIN_CHAR = 97; // 'a'
    private static final int MAX_CHAR = 122; // 'z'

    /**
     * Возвращает строку, которая лексикографически находится между prev и next
     */
    public static String getBetween(String prev, String next) {
        if (prev == null && next == null) return "m"; // Первая задача на доске
        if (prev == null) return getBefore(next);     // Вставили в самый верх
        if (next == null) return getAfter(prev);      // Вставили в самый низ

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (true) {
            int pChar = i < prev.length() ? prev.charAt(i) : MIN_CHAR - 1;
            int nChar = i < next.length() ? next.charAt(i) : MAX_CHAR + 1;

            if (pChar == nChar) {
                result.append((char) pChar);
                i++;
                continue;
            }

            int mid = (pChar + nChar) / 2;
            if (mid == pChar) {
                result.append((char) pChar);
                result.append('n');
            } else {
                result.append((char) mid);
            }
            break;
        }
        return result.toString();
    }

    private static String getBefore(String next) {
        return getBetween("a", next);
    }

    private static String getAfter(String prev) {
        return prev + "n";
    }
}