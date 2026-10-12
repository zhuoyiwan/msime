package app.msime.android;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 数字页预设和有边界的自定义字符，保留功能键及拼音字母映射。 */
public final class KeyboardArrangementPolicy {
    public static final String PHONE = "phone";
    public static final String CHAT = "chat";
    public static final String CALCULATOR = "calculator";
    public static final String ROW = "row";
    public static final String CUSTOM = "custom";
    public static final String DEFAULT_DIGITS = "1234567890";
    public static final String DEFAULT_SYMBOLS = "[ ] { } # % ^ * + =\n_ \\ | ~ < > € & @ !";
    private KeyboardArrangementPolicy() {}

    public static String digits(String preset, String custom) {
        if (CALCULATOR.equals(preset)) return "7894561230";
        if (CUSTOM.equals(preset) && validDigits(custom)) return custom.trim();
        return DEFAULT_DIGITS;
    }

    public static boolean validDigits(String value) {
        if (value == null || value.trim().length() != 10) return false;
        String digits = value.trim();
        boolean[] seen = new boolean[10];
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            if (digit < 0 || digit > 9 || seen[digit]) return false;
            seen[digit] = true;
        }
        return true;
    }

    public static List<List<String>> symbolRows(String text) {
        if (text == null || text.length() > 512) return null;
        String[] rows = text.trim().split("\\R", -1);
        if (rows.length != 2) return null;
        List<List<String>> result = new ArrayList<>(2);
        for (String row : rows) {
            if (row.isBlank()) return null;
            String[] keys = row.trim().split("\\s+");
            if (keys.length < 4 || keys.length > 10) return null;
            HashSet<String> seen = new HashSet<>();
            for (String key : keys) {
                if (key.codePointCount(0, key.length()) > 4 || !seen.add(key)) return null;
                if (key.codePoints().anyMatch(Character::isISOControl)) return null;
            }
            result.add(List.of(keys));
        }
        return List.copyOf(result);
    }

    public static String normalizeSymbols(String value) {
        List<List<String>> rows = symbolRows(value);
        return rows == null ? "" : String.join(" ", rows.get(0)) + "\n" + String.join(" ", rows.get(1));
    }

    public static String title(String preset) {
        return switch (preset) {
            case CHAT -> "聊天常用（数字旁带标点）";
            case CALCULATOR -> "计算器式（789 / 456 / 123）";
            case ROW -> "数字横排（数字与常用符号）";
            case CUSTOM -> "自定义数字顺序";
            default -> "电话式（123 / 456 / 789）";
        };
    }
}
