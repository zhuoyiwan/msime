package app.msime.android.home;

import app.msime.android.DimensionPolicy;
import app.msime.android.AndroidLocalSettings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import app.msime.android.R;
import org.json.JSONObject;

/**
 * 软件界面的独立深浅模式，读取本地 APP_COLOR_MODE；键盘单独解析本机模式与共享偏好。
 * 季节和壁纸配色保持原有缓存与叠加方式。
 */
final class AppMode {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    private static final String STORE = "msime_home_v1";
    /** 应用主题最近一次解析到的季节（`spring`、`summer`、`autumn`、`winter`），由应用主题控制器在读到共享偏好后写入；没有时用基础主题的秋杉。 */
    static final String SEASON_KEY = "app_theme_season";

    private AppMode() {}

    /** 恢复上次应用的深浅模式，并给这个 activity 的主题叠上缓存季节的配色。每个宿主 activity 都在 `super.onCreate` 之前调用它，所以进程被恢复到任何一个页面时，第一帧就是对的模式和季节。 */
    static void restore(Context context) {
        SharedPreferences store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        apply(of(context));
        int overlay = seasonOverlay(store.getString(SEASON_KEY, null));
        // 基础主题本身就是秋杉，没有缓存时不必再叠一层。
        if (overlay != 0) context.getTheme().applyStyle(overlay, true);
        String season = store.getString(SEASON_KEY, "");
        if (season != null && season.startsWith("wallpaper-") && context instanceof android.app.Activity)
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable((android.app.Activity) context);
    }

    /** 季节对应的主题叠加层；不认识的值和 null 都回到基础主题的秋杉，返回 0 表示不用叠加。 */
    private static int seasonOverlay(@Nullable String season) {
        if (season == null) return 0;
        switch (season) {
            case "spring": return R.style.ThemeOverlay_MSIME_Season_Spring;
            case "summer": return R.style.ThemeOverlay_MSIME_Season_Summer;
            case "autumn": return R.style.ThemeOverlay_MSIME_Season_Autumn;
            case "winter": return R.style.ThemeOverlay_MSIME_Season_Winter;
            default: return 0;
        }
    }

    /** 读取软件独立模式；参数保留给已有加载流程，不再从键盘偏好取色。 */
    static void follow(Context context, @Nullable JSONObject preferences) {
        String mode = of(context);
        apply(mode);
    }

    /** 软件默认独立跟随系统，未知值由本地设置层恢复默认。 */
    static String of(Context context) {
        return AndroidLocalSettings.load(context).choice(AndroidLocalSettings.APP_COLOR_MODE);
    }

    /** 这个软件界面当前的模式，不用于推断键盘或设备模式。 */
    static boolean dark(Context context) {
        return DimensionPolicy.isNight(context);
    }

    private static void apply(String mode) {
        int night = LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
            : DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
            : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        int current = AppCompatDelegate.getDefaultNightMode();
        // An app that never set a mode is already following the system; setting it anyway would walk every open activity for nothing.
        if (current == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) current = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (current != night) AppCompatDelegate.setDefaultNightMode(night);
    }
}
