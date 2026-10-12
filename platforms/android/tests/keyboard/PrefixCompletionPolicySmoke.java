import app.msime.android.PrefixCompletionPolicy;
import java.util.List;

public final class PrefixCompletionPolicySmoke {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        var phone = new PrefixCompletionPolicy.Entry("1", "13800000000", "dh");
        var name = new PrefixCompletionPolicy.Entry("2", "测试姓名", "csxm");
        var identity = new PrefixCompletionPolicy.Entry("3", "11000020000101000X", "sfz");
        var list = List.of(phone, name, identity);
        check(PrefixCompletionPolicy.match(list, "", "13") == null);
        var result = PrefixCompletionPolicy.match(list, "", "号码：138");
        check(result.prefix().equals("138") && !result.composing() && result.entries().equals(List.of(phone)));
        check(PrefixCompletionPolicy.match(list, "cs", null).entries().equals(List.of(name)));
        check(PrefixCompletionPolicy.match(list, "", "110000").entries().equals(List.of(identity)));
        check(PrefixCompletionPolicy.match(list, "", "测试").entries().equals(List.of(name)));
        check(PrefixCompletionPolicy.match(list, "", "13800000000") == null);
        check(PrefixCompletionPolicy.match(list, "", "x138") == null);
        check(PrefixCompletionPolicy.match(list, "", "138 ") == null);
        check(!PrefixCompletionPolicy.validAlias("bad code"));
        check(!PrefixCompletionPolicy.validAlias("ABC"));
        check(PrefixCompletionPolicy.validAlias("sfz"));
        System.out.println("Prefix completion policy passed");
    }
}
