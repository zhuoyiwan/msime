package app.msime.android;

import org.json.JSONException;
import org.json.JSONObject;

/** Resolves one app theme palette through the native host's strict JSON envelope. */
public final class AppThemeResolver {
    private AppThemeResolver() {}

    /** 壁纸是安卓本机主题，其他主题继续交给共享解析器。 */
    public static JSONObject resolve(android.content.Context context, String theme, int month, boolean dark) {
        return "wallpaper".equals(theme) ? WallpaperTheme.resolve(context, dark) : resolve(theme, month, dark);
    }

    /** Returns the resolved palette, or null when the native host is unavailable or rejects it. */
    public static JSONObject resolve(String theme, int month, boolean dark) {
        try {
            JSONObject root = new JSONObject(NativeClient.resolveAppTheme(theme, month, dark));
            return JsonPolicy.strictTrue(root.opt("ok"))
                ? root.optJSONObject("value") : null;
        } catch (JSONException | RuntimeException | LinkageError error) {
            return null;
        }
    }
}
