package app.msime.android;

import android.content.Context;

/** 安卓改版的产品边界：不启用上游账号、匿名身份或云同步。 */
public final class LocalForkMode {
    private LocalForkMode() {}
    public static boolean accountServicesEnabled() { return false; }
    public static void clearLegacyAccounts(Context context) {
        for (String store : new String[]{"msime_account_session_v1", "msime_account_session_v2", "msime_sync_v1", "msime_anonymous_session_v1", "msime_anonymous_account_v1"})
            context.getSharedPreferences(store, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("msime_home_v1", Context.MODE_PRIVATE).edit().putBoolean("update_auto", false).apply();
        context.getSystemService(android.app.job.JobScheduler.class).cancel(0x6d730401);
    }
}
