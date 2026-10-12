package app.msime.android.home;

import android.content.Context;
import app.msime.android.*;
import app.msime.android.policy.HostOptionsPolicy;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** 本地备份与词库页面共用的原生数据适配，从云同步控制器中独立出来。 */
final class LocalData {
    private LocalData() {}
    static String hostOptions(Context context) throws IOException {
        return HostOptionsPolicy.read(new File(context.getFilesDir(), "runtime-options.json"));
    }
    static JSONObject nativeValue(String response) throws JSONException {
        JSONObject root = new JSONObject(response == null ? "" : response);
        if (!JsonPolicy.strictTrue(root.opt("ok"))) throw new IllegalStateException(root.optString("error", "本机操作失败"));
        JSONObject value = root.optJSONObject("value");
        return value == null ? new JSONObject() : value;
    }
    static JSONObject hostFeedback(Context context) throws JSONException {
        KeyboardFeedbackStore.Settings settings = KeyboardFeedbackStore.load(context);
        return new JSONObject().put("soundEnabled", settings.soundEnabled()).put("hapticsEnabled", settings.hapticsEnabled())
            .put("hapticStrength", settings.hapticStrength().id());
    }
    static Map<String,Object> map(JSONObject document) {
        LinkedHashMap<String,Object> result = new LinkedHashMap<>();
        Iterator<String> keys = document.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = document.opt(key);
            if (value instanceof String || value instanceof Boolean || value instanceof Number) result.put(key, value);
        }
        return result;
    }
    static void applySettings(Context context, String directory, JSONObject document, JSONObject schema) throws IOException, JSONException {
        JSONObject value = nativeValue(NativeClient.accountSettingsApply(new JSONObject().put("preferences_directory", directory)
            .put("cloud", document).put("schema", schema).put("feedback", hostFeedback(context)).toString()));
        JSONObject applied = value.optJSONObject("feedback");
        if (applied != null) KeyboardFeedbackStore.save(context, KeyboardFeedbackStore.fromValues(applied.opt("soundEnabled"), applied.opt("hapticsEnabled"), applied.opt("hapticStrength")));
        if (value.opt("custom_keyboard_skins") instanceof String library) CustomSkinLibrary.importDesigns(Paths.get(directory), library);
        JSONObject local = value.optJSONObject("android_local");
        if (local != null) AndroidLocalSettings.applySynced(context, map(local));
    }
    static JSONObject exportDictionarySnapshot(Context context, Path destination, boolean includeLearning) throws IOException, JSONException {
        Files.deleteIfExists(destination);
        JSONObject action = new JSONObject().put("operation", "export_snapshot").put("destination", destination.toAbsolutePath().toString());
        if (includeLearning) action.put("include_learning", true);
        return nativeValue(NativeClient.dictionary(new JSONObject().put("options", new JSONObject(hostOptions(context))).put("action", action).toString()));
    }
    static int userWordCount(Context context) throws IOException, JSONException {
        JSONObject value = nativeValue(NativeClient.dictionary(new JSONObject().put("options", new JSONObject(hostOptions(context)))
            .put("action", new JSONObject().put("operation", "count").put("user_only", true)).toString()));
        Integer count = JsonPolicy.nonNegativeInteger(value.opt("count"));
        return count == null ? 0 : count;
    }
    static void enqueueDictionarySnapshot(Context context, Path file, String owner, long revision) throws IOException, DictionarySnapshotQueue.Failure {
        Path root = context.getFilesDir().toPath().toAbsolutePath().normalize();
        DictionarySnapshotQueue queue = new DictionarySnapshotQueue(root, root.resolve("bootstrap/state/dictionary-snapshots"));
        String version = queue.read().localVersion();
        if (version == null) throw new IOException("本机词库版本尚未准备");
        queue.enqueue(file.toAbsolutePath(), owner, revision, version, DigestPolicy.sha256Hex(file));
    }
}
