package app.msime.android;

import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.os.Build;
import com.google.android.material.color.ColorRoles;
import com.google.android.material.color.MaterialColors;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** 本机壁纸配色：新系统直接使用 Monet，旧系统只读取公开的主色，不读取壁纸图片。 */
public final class WallpaperTheme {
    private WallpaperTheme() {}

    public static JSONObject resolve(Context context, boolean dark) {
        int seed = 0xFF6079A8;
        int fingerprint;
        int accent, background, card, hair, onAccent;
        if (Build.VERSION.SDK_INT >= 31) {
            android.content.res.Resources resources = context.getResources();
            seed = resources.getColor(android.R.color.system_accent1_500, context.getTheme());
            accent = resources.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600, context.getTheme());
            background = resources.getColor(dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_10, context.getTheme());
            card = resources.getColor(dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50, context.getTheme());
            hair = resources.getColor(dark ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_200, context.getTheme());
            onAccent = resources.getColor(dark ? android.R.color.system_accent1_900 : android.R.color.system_accent1_10, context.getTheme());
            fingerprint = 31 * seed + resources.getColor(android.R.color.system_neutral1_500, context.getTheme());
        } else {
            try {
                WallpaperColors colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null) seed = colors.getPrimaryColor().toArgb();
            } catch (RuntimeException ignored) {
                // 厂商拒绝访问壁纸颜色时仍提供稳定的回退，不申请照片或存储权限。
            }
            ColorRoles roles = MaterialColors.getColorRoles(seed, !dark);
            accent = roles.getAccent();
            onAccent = roles.getOnAccent();
            background = mix(seed, dark ? 0xFF101114 : 0xFFFAFAFC, dark ? 0.08f : 0.03f);
            card = mix(seed, dark ? 0xFF1C1D20 : 0xFFF2F3F5, dark ? 0.10f : 0.05f);
            hair = mix(seed, dark ? 0xFF45464B : 0xFFCCCDD2, 0.12f);
            fingerprint = seed;
        }
        try {
            return new JSONObject().put("id", "wallpaper")
                .put("season", "wallpaper-" + Integer.toHexString(fingerprint))
                .put("accent", css(accent)).put("background", css(background))
                .put("card", css(card)).put("hair", css(hair)).put("on_accent", css(onAccent));
        } catch (JSONException error) {
            return null;
        }
    }

    private static String css(int color) { return String.format(Locale.ROOT, "#%06x", color & 0xFFFFFF); }

    private static int mix(int seed, int base, float weight) {
        int rgb = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8)
            rgb |= Math.round(((seed >> shift) & 255) * weight + ((base >> shift) & 255) * (1 - weight)) << shift;
        return rgb;
    }
}
