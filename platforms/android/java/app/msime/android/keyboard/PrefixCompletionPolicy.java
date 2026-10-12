package app.msime.android;

import java.util.ArrayList;
import java.util.List;

/** 只匹配手动词条；上文边界、最短前缀和替换范围由这里统一决定。 */
public final class PrefixCompletionPolicy {
    public record Entry(String id, String text, String alias) {}
    public record Match(String prefix, boolean composing, List<Entry> entries) {}
    public static final int MAX_CONTEXT = 200;
    private PrefixCompletionPolicy() {}

    public static boolean validAlias(String value) {
        return value != null && value.length() <= 32 && value.chars().allMatch(c ->
            c >= 'a' && c <= 'z' || c >= '0' && c <= '9');
    }

    public static Match match(List<Entry> entries, String composition, CharSequence before) {
        boolean composing = composition != null && !composition.isEmpty();
        String prefix = composing ? composition : tail(before);
        if (prefix.isEmpty() || prefix.length() > MAX_CONTEXT) return null;
        boolean numeric = prefix.chars().allMatch(c -> c >= '0' && c <= '9');
        if (numeric && prefix.length() < 3) return null;
        List<Entry> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.text().length() > MAX_CONTEXT || entry.text().indexOf('\n') >= 0) continue;
            boolean alias = !entry.alias().isEmpty() && entry.alias().startsWith(prefix)
                && (prefix.length() >= 2 || entry.alias().equals(prefix));
            boolean literal = entry.text().startsWith(prefix) && !entry.text().equals(prefix);
            if ((alias || literal) && !found.stream().anyMatch(e -> e.text().equals(entry.text()))) found.add(entry);
            if (found.size() == 5) break;
        }
        return found.isEmpty() ? null : new Match(prefix, composing, List.copyOf(found));
    }

    private static String tail(CharSequence text) {
        if (text == null) return "";
        String value = text.toString();
        int index = value.length();
        while (index > 0 && Character.isLetterOrDigit(value.charAt(index - 1))) index--;
        return value.substring(index);
    }
}
