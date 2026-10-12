package app.msime.android.test;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Predicate;
import org.json.JSONObject;

/** 专用模拟器上的表情上屏、分隔拼音、关闭开关，以及壁纸主题换色验收。 */
public final class EmojiWallpaperDeviceSmoke extends DeviceSmoke {
    private File preferences;
    private File localSettings;
    @Override protected String successDescription() { return "emoji commit, separated pinyin, "
        + (android.os.Build.VERSION.SDK_INT < 30 ? "live disabled toggle" : "disabled toggle after restart")
        + " and wallpaper refresh in both modes"; }
    @Override protected void runChecks() throws Exception {
        getTargetContext().getSharedPreferences("msime_onboarding_v1", Context.MODE_PRIVATE).edit().putBoolean("seen", true).commit();
        File root = getTargetContext().getFilesDir();
        JSONObject options = read(new File(root, "runtime-options.json"));
        preferences = new File(options.getString("preferences_directory"), "preferences.json");
        localSettings = new File(root, "bootstrap/state/android-settings.json");
        byte[] originalPreferences = Files.readAllBytes(preferences.toPath());
        byte[] originalLocal = localSettings.exists() ? Files.readAllBytes(localSettings.toPath()) : null;
        try {
            stage = "enable emoji";
            setEmoji(true); rebind(); openEditor();
            typePinyin("sikao");
            stage = "thinking emoji commit";
            tapThinkingEmoji();
            await(field("msime-test-plain").and(node -> equalsText("🤔", node.getText())));
            stage = "separated pinyin";
            openEditor();
            typePinyin("si'kao");
            stage = "separated emoji consumes composition";
            tapThinkingEmoji();
            await(field("msime-test-plain").and(node -> equalsText("🤔", node.getText())));
            stage = "emoji leaves no stale composition";
            typePinyin("n");
            await(field("msime-test-plain").and(node -> equalsText("🤔n", node.getText())));
            tapEditingKey("⌫", KeyEvent.KEYCODE_DEL);
            stage = "disabled emoji survives restart";
            setEmoji(false);
            if (android.os.Build.VERSION.SDK_INT >= 30) { rebind(); openEditor(); }
            else {
                // API 28 另测实时开关；重启后的持久化由 API 35 验收，避免旧无障碍缓存跨重绑。
                stage = "disabled emoji live reload";
                tapEditingKey("⌫", KeyEvent.KEYCODE_DEL);
                SystemClock.sleep(2500);
            }
            typePinyin("sikao");
            if (android.os.Build.VERSION.SDK_INT >= 30) await(appText("思考"));
            SystemClock.sleep(400);
            for (var window : automation.getWindows())
                if (find(window.getRoot(), appText("🤔")) != null) throw new AssertionError("Disabled emoji visible");
            tapEditingKey("空格", KeyEvent.KEYCODE_SPACE);
            await(field("msime-test-plain").and(node -> equalsText("思考", node.getText())));
            stage = "wallpaper light and dark";
            JSONObject document = localSettings.exists() ? read(localSettings) : new JSONObject().put("version", 1).put("settings", new JSONObject());
            document.getJSONObject("settings").put("platform.android.wallpaper_colors", true);
            publish(localSettings, document.toString().getBytes(StandardCharsets.UTF_8));
            shell("am start -W -n app.msime.android/app.msime.android.home.HomeActivity"); SystemClock.sleep(2000);
            JSONObject light = resolve(false), dark = resolve(true);
            if (!"wallpaper".equals(light.getString("id")) || !light.getString("season").equals(dark.getString("season")))
                throw new AssertionError("Wallpaper mode fingerprint mismatch");
            if (light.getString("background").equals(dark.getString("background"))) throw new AssertionError("Dark theme stayed light");
            stage = "wallpaper refresh";
            String previous = light.getString("season");
            android.content.SharedPreferences fixture = getTargetContext().getSharedPreferences("msime_wallpaper_fixture", Context.MODE_PRIVATE);
            int wallpaperSeed = fixture.getInt("seed", -4259649) == -4259649 ? -13408615 : -4259649;
            fixture.edit().putInt("seed", wallpaperSeed).commit();
            // 设置壁纸后立即 finish 的夹具没有首帧，API 28 的 am start -W 会一直等首帧。
            shell("am start -n app.msime.android.test/app.msime.android.test.WallpaperSetterActivity --ei seed " + wallpaperSeed);
            long deadline = SystemClock.uptimeMillis() + 20000;
            boolean changed = false;
            while (SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(500);
                if (!previous.equals(resolve(false).getString("season"))) { changed = true; break; }
            }
            if (!changed) throw new AssertionError("Wallpaper palette did not refresh");
            stage = "wallpaper keyboard remains usable";
            shell("ime set app.msime.android/.MSIMEInputService");
            openEditor(); await(key("n"));
        } finally {
            publish(preferences, originalPreferences);
            if (originalLocal != null) publish(localSettings, originalLocal); else Files.deleteIfExists(localSettings.toPath());
        }
    }
    private JSONObject resolve(boolean dark) throws Exception {
        Class<?> resolver = Class.forName("app.msime.android.WallpaperTheme", true, getTargetContext().getClassLoader());
        return (JSONObject) resolver.getMethod("resolve", Context.class, boolean.class).invoke(null, getTargetContext(), dark);
    }
    private static JSONObject read(File file) throws Exception { return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)); }
    private void setEmoji(boolean enabled) throws Exception {
        JSONObject document = read(preferences), values = document.getJSONObject("preferences");
        JSONObject mixed = values.optJSONObject("mixed_input"); if (mixed == null) mixed = new JSONObject();
        mixed.put("emoji", enabled); values.put("mixed_input", mixed).put("global_theme", "system");
        document.put("revision", document.getLong("revision") + 1);
        publish(preferences, document.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Predicate<AccessibilityNodeInfo> appText(String text) { return node -> equalsText("app.msime.android", node.getPackageName()) && equalsText(text, node.getText()); }
    private void openEditor() throws Exception {
        shell("am start -W -n app.msime.android.test/app.msime.android.test.EditorActivity");
        // 旧系统的任务切换完成前仍可能返回上一个编辑器的节点，先等新窗口布局稳定。
        SystemClock.sleep(800);
        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        if (!await(field("msime-test-plain")).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            throw new AssertionError("Editor fixture clear rejected");
        tap(field("msime-test-plain"));
        if (android.os.Build.VERSION.SDK_INT >= 30)
            await(field("msime-test-plain").and(AccessibilityNodeInfo::isFocused));
        else SystemClock.sleep(1500);
        // 冷启动时字母键先出现，引擎与实时偏好仍在后台准备；先等方案入口可用再输入。
        await(describedPrefix("输入方案：").and(AccessibilityNodeInfo::isEnabled));
    }
    private void typePinyin(String pinyin) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 30 || pinyin.indexOf('\'') >= 0) {
            KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(pinyin.toCharArray());
            for (KeyEvent event : events) automation.injectInputEvent(event, true);
        } else {
            for (int index = 0; index < pinyin.length(); index++) tap(key(pinyin.substring(index, index + 1)));
        }
    }
    private void tapThinkingEmoji() throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            tap(appText("🤔"));
            return;
        }
        // API 28 的动态候选条不出现在测试无障碍树里。专用模拟器使用默认字号，
        // 按字母行的位置点「思考」后的第二候选；后续仍严格断言实际编辑器收到 🤔。
        SystemClock.sleep(500);
        android.graphics.Rect firstKey = new android.graphics.Rect();
        await(key("q")).getBoundsInScreen(firstKey);
        int x = Math.round(getTargetContext().getResources().getDisplayMetrics().widthPixels * 0.195f);
        int y = firstKey.top - Math.round(getTargetContext().getResources().getDisplayMetrics().density * 32);
        long now = SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {
            if (!automation.injectInputEvent(down, true) || !automation.injectInputEvent(up, true))
                throw new AssertionError("Emoji fixture touch rejected");
        } finally { down.recycle(); up.recycle(); }
    }
    private void tapEditingKey(String label, int code) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            tap(key(label));
        } else {
            android.graphics.Rect bounds = new android.graphics.Rect();
            awaitAny(key(label)).getBoundsInScreen(bounds);
            long now = SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
            down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try {
                if (!automation.injectInputEvent(down, true) || !automation.injectInputEvent(up, true))
                    throw new AssertionError("Editing fixture touch rejected");
            } finally { down.recycle(); up.recycle(); }
            SystemClock.sleep(300);
        }
    }
    private void rebind() throws Exception {
        shell("ime disable app.msime.android/.MSIMEInputService"); shell("ime enable app.msime.android/.MSIMEInputService");
        shell("ime set app.msime.android/.MSIMEInputService"); SystemClock.sleep(800);
    }
    private void shell(String command) throws Exception {
        try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            byte[] buffer = new byte[1024]; while (stream.read(buffer) != -1) { }
        }
    }
    private static void publish(File file, byte[] bytes) throws Exception {
        AtomicFile target = new AtomicFile(file); FileOutputStream stream = target.startWrite();
        try { stream.write(bytes); target.finishWrite(stream); } catch (Exception error) { target.failWrite(stream); throw error; }
    }
}
