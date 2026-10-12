package app.msime.android;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import com.google.android.material.color.MaterialColors;

/** Shared Material theme colour lookups used by Android host surfaces. */
public final class ThemeColorPolicy {
    private static String wallpaperKey = "";
    private static AppThemePalette.Seed wallpaperSeed;
    private ThemeColorPolicy() { }

    /** 读一个颜色主题属性；属性缺失时退回洋红，让漏配的属性在截图里一眼可见，而不是悄悄显示成别的颜色。 */
    @ColorInt public static int color(Context context, @AttrRes int attr) {
        AppThemePalette palette = wallpaperPalette(context);
        if (palette != null) {
            if (attr == androidx.appcompat.R.attr.colorPrimary) return palette.accent;
            if (attr == com.google.android.material.R.attr.colorOnPrimary) return palette.onAccent;
            if (attr == com.google.android.material.R.attr.colorPrimaryContainer) return palette.accentSoft;
            if (attr == com.google.android.material.R.attr.colorSurface) return palette.background;
            if (attr == com.google.android.material.R.attr.colorSurfaceContainer) return palette.card;
            if (attr == com.google.android.material.R.attr.colorSurfaceContainerLowest) return palette.rowBackground;
            if (attr == com.google.android.material.R.attr.colorSurfaceContainerLow) return palette.card;
            if (attr == com.google.android.material.R.attr.colorOnSurface) return palette.text;
            if (attr == com.google.android.material.R.attr.colorOnSurfaceVariant) return palette.sub;
            if (attr == com.google.android.material.R.attr.colorOutline) return palette.outline;
            if (attr == com.google.android.material.R.attr.colorOutlineVariant) return palette.hair;
        }
        return MaterialColors.getColor(context, attr, Color.MAGENTA);
    }

    /** 缓存序列化种子，绘制列表时不逐个颜色重复解析 JSON。 */
    private static synchronized AppThemePalette wallpaperPalette(Context context) {
        android.content.SharedPreferences store = context.getSharedPreferences("msime_home_v1", Context.MODE_PRIVATE);
        String season = store.getString("app_theme_season", "");
        if (season == null || !season.startsWith("wallpaper-")) return null;
        String key = store.getString("app_theme_seed_light", "") + store.getString("app_theme_seed_dark", "");
        if (!key.equals(wallpaperKey) || wallpaperSeed == null) {
            wallpaperSeed = app.msime.android.home.HostStore.seed(context);
            wallpaperKey = key;
        }
        return AppThemePalette.of(wallpaperSeed, DimensionPolicy.isNight(context));
    }

    @ColorInt public static int accent(Context context) {
        return color(context, androidx.appcompat.R.attr.colorPrimary);
    }

    @ColorInt public static int onAccent(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnPrimary);
    }

    @ColorInt public static int accentSoft(Context context) {
        return color(context, com.google.android.material.R.attr.colorPrimaryContainer);
    }

    @ColorInt public static int card(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainer);
    }

    @ColorInt public static int rowBackground(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainerLowest);
    }

    @ColorInt public static int sheetBackground(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainerLow);
    }

    @ColorInt public static int page(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurface);
    }

    @ColorInt public static int text(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnSurface);
    }

    @ColorInt public static int subText(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    @ColorInt public static int outline(Context context) {
        return color(context, com.google.android.material.R.attr.colorOutline);
    }

    @ColorInt public static int hairline(Context context) {
        return color(context, com.google.android.material.R.attr.colorOutlineVariant);
    }

    @ColorInt public static int danger(Context context) {
        return color(context, androidx.appcompat.R.attr.colorError);
    }

}
