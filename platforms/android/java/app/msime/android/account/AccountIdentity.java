package app.msime.android;

import android.content.Context;

/** 共享社区组件的兼容类型；本改版不生成或注册匿名账号。 */
public final class AccountIdentity {
    private AccountIdentity() {}
    public static String subject(Context context) { return ""; }
    public static void register(Context context) { }
    public static String shortSubject(String subject) { return ""; }
}
