import app.msime.android.CompactKeyboardPolicy;

public final class CompactKeyboardPolicySmoke {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        int limit = CompactKeyboardPolicy.heightLimit(700, 400, true);
        check(limit == 272);
        check(CompactKeyboardPolicy.heightLimit(limit, 400, true) == limit);
        check(CompactKeyboardPolicy.heightLimit(200, 400, true) == 200);
        check(CompactKeyboardPolicy.heightLimit(200, 0, true) == 200);
        check(CompactKeyboardPolicy.heightLimit(0, 0, false) == Integer.MAX_VALUE);
        check(CompactKeyboardPolicy.keyScale(500, 400, 300) == .5f);
        check(CompactKeyboardPolicy.keyScale(500, 400, 90) == 0f);
        check(CompactKeyboardPolicy.keyScale(250, 150, 300) == 1f);
        check(CompactKeyboardPolicy.heightLimit(700, 800, false) == 700);
        System.out.println("Compact keyboard height budget passed");
    }
}
