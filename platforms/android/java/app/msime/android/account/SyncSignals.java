package app.msime.android;

import android.content.Context;

/** 本地数据组件的兼容接口；改版不启动账号 provider 或记录云同步任务。 */
public final class SyncSignals {
    public record State(boolean enabled, String loginKind) {
        public static final State OFF = new State(false, "");
    }
    private SyncSignals() {}
    public static boolean markDirty(Context context, String section) { return false; }
    public static State state(Context context) { return State.OFF; }
    public static SyncSwitch.Binding binding(Context context) { return new SyncSwitch.Binding("", 0); }
    public static String accountId(Context context) { return ""; }
    static State stateOf(boolean enabled, String loginKind) { return State.OFF; }
}
