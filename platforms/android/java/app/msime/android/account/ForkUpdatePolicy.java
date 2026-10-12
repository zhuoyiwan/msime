package app.msime.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 只从维护者的 GitHub 发布中选择本改版 ARM64 安装包，忽略其他平台、草稿与上游包。 */
public final class ForkUpdatePolicy {
    public static final String DOWNLOAD_PREFIX = "https://github.com/zhuoyiwan/msime-windows/releases/download/";
    private static final Pattern ASSET = Pattern.compile(
        "MetasequoiaIME_Android_v(\\d+\\.\\d+\\.\\d+)-zhuoyiwan-arm64-r(\\d+)-signed\\.apk");
    private static final Pattern VERSION = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+)-zhuoyiwan-r(\\d+)");
    private static final Pattern TAG = Pattern.compile("zhuoyiwan-[A-Za-z0-9._-]{1,100}");

    private ForkUpdatePolicy() {}

    public static int compare(String left, String right) {
        Matcher a = VERSION.matcher(left);
        Matcher b = VERSION.matcher(right);
        if (!a.matches() || !b.matches()) return UpdateApi.compareVersions(left, right);
        int core = UpdateApi.compareVersions(a.group(1), b.group(1));
        return core != 0 ? core : UpdateApi.compareVersions(a.group(2), b.group(2));
    }

    public static UpdateApi.Update pick(String json, UpdateApi.Channel channel, String edition,
            String currentVersion) throws UpdateApi.Failure {
        if (!"full".equals(edition)) return null;
        try {
            JSONArray releases = new JSONArray(json);
            UpdateApi.Update best = null;
            for (int index = 0; index < releases.length(); index++) {
                JSONObject item = releases.optJSONObject(index);
                if (item == null || !Boolean.FALSE.equals(item.opt("draft"))) continue;
                String tag = JsonPolicy.strictString(item.opt("tag_name"));
                Object preview = item.opt("prerelease");
                if (tag == null || !TAG.matcher(tag).matches() || !(preview instanceof Boolean)) continue;
                JSONArray assets = item.optJSONArray("assets");
                if (assets == null) continue;
                for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                    JSONObject asset = assets.optJSONObject(assetIndex);
                    if (asset == null) continue;
                    String name = JsonPolicy.strictString(asset.opt("name"));
                    if (name == null) continue;
                    Matcher match = ASSET.matcher(name);
                    if (!match.matches()) continue;
                    String version = match.group(1) + "-zhuoyiwan-r" + match.group(2);
                    UpdateApi.Release release = new UpdateApi.Release(tag, version, (Boolean) preview);
                    if (!channel.accepts(release) || compare(version, currentVersion) <= 0) continue;
                    String url = DOWNLOAD_PREFIX + tag + "/" + name;
                    if (!url.equals(JsonPolicy.strictString(asset.opt("browser_download_url")))) continue;
                    Long size = JsonPolicy.strictLong(asset.opt("size"));
                    if (size == null || size <= 0 || size > UpdateApi.MAX_APK_BYTES) continue;
                    String digest = JsonPolicy.strictString(asset.opt("digest"));
                    if (digest == null || !digest.matches("sha256:[0-9a-f]{64}")) continue;
                    if (best == null || compare(version, best.release().version()) > 0) {
                        best = new UpdateApi.Update(release, url, "", name, digest.substring(7));
                    }
                }
            }
            return best;
        } catch (JSONException malformed) {
            throw new UpdateApi.Failure("GitHub 发布列表格式不对", malformed);
        }
    }
}
