import app.msime.android.ForkUpdatePolicy;
import app.msime.android.PlatformSpeechPolicy;
import app.msime.android.UpdateApi;
import org.json.JSONArray;
import org.json.JSONObject;

/** 改版资产筛选、修订号和系统识别语言的回归。 */
public final class ForkUpdatePolicySmoke {
    public static void main(String[] args) throws Exception {
        check(ForkUpdatePolicy.compare("0.3.1-zhuoyiwan-r10", "0.3.1-zhuoyiwan-r9") > 0);
        String name = "MetasequoiaIME_Android_v0.3.1-zhuoyiwan-arm64-r6-signed.apk";
        String tag = "zhuoyiwan-20261013-r3";
        JSONObject asset = new JSONObject().put("name", name).put("size", 150_000_000)
            .put("digest", "sha256:" + "a".repeat(64))
            .put("browser_download_url", ForkUpdatePolicy.DOWNLOAD_PREFIX + tag + "/" + name);
        JSONObject release = new JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", true)
            .put("assets", new JSONArray().put(asset));
        String data = new JSONArray().put(release).toString();
        check(ForkUpdatePolicy.pick(data, UpdateApi.Channel.PREVIEW, "full", "0.3.1-zhuoyiwan-r5") != null);
        check(ForkUpdatePolicy.pick(data, UpdateApi.Channel.STABLE, "full", "0.3.1-zhuoyiwan-r5") == null);
        check(ForkUpdatePolicy.pick(data, UpdateApi.Channel.PREVIEW, "full", "0.3.1-zhuoyiwan-r6") == null);
        check(ForkUpdatePolicy.pick(data, UpdateApi.Channel.PREVIEW, "wubi", "0.3.1-zhuoyiwan-r5") == null);
        for (String invalid : new String[] {"https://evil.example/x", ForkUpdatePolicy.DOWNLOAD_PREFIX + "other/x.apk"}) {
            asset.put("browser_download_url", invalid);
            check(ForkUpdatePolicy.pick(new JSONArray().put(release).toString(), UpdateApi.Channel.PREVIEW,
                "full", "0.3.1-zhuoyiwan-r5") == null);
        }
        check("zh-CN".equals(PlatformSpeechPolicy.languageTag("auto", "zh-CN")));
        check("en-US".equals(PlatformSpeechPolicy.languageTag("en", "zh-CN")));
        check("yue-Hant-HK".equals(PlatformSpeechPolicy.languageTag("yue", "zh-CN")));
        check("zh-CN".equals(PlatformSpeechPolicy.languageTag("bad!", "en-US")));
        System.out.println("Fork update and platform speech language passed");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
