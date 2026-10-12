package app.msime.android.test;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
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
import java.util.List;
import org.json.JSONObject;

/** 横屏高度、预设数字布局和本机词条补全的真实编辑器验收。 */
public final class CompactPrefixDeviceSmoke extends DeviceSmoke {
    private File localSettings;
    private JSONObject local;

    @Override protected String successDescription() {
        if (android.os.Build.VERSION.SDK_INT < 30) return "phone prefix completion and landscape Chinese commit (API 28)";
        return "landscape constrained candidates, portrait recovery, numeric layouts, phone and name prefix commit and password exclusion";
    }

    @Override protected void runChecks() throws Exception {
        getTargetContext().getSharedPreferences("msime_onboarding_v1", Context.MODE_PRIVATE)
            .edit().putBoolean("seen", true).commit();
        File root = getTargetContext().getFilesDir();
        localSettings = new File(root, "bootstrap/state/android-settings.json");
        byte[] original = localSettings.exists() ? Files.readAllBytes(localSettings.toPath()) : null;
        local = original == null ? new JSONObject().put("version", 1).put("settings", new JSONObject())
            : new JSONObject(new String(original, StandardCharsets.UTF_8));
        Class<?> store = Class.forName("app.msime.android.CommonPhrasesStore", true, getTargetContext().getClassLoader());
        String[] fixtures = {"13800000000", "测试姓名", "11000020000101000X"};
        try {
            stage = "save synthetic phrases";
            for (String text : fixtures) store.getMethod("add", Context.class, String.class).invoke(null, getTargetContext(), text);
            Object result = store.getMethod("load", Context.class).invoke(null, getTargetContext());
            Object document = result.getClass().getMethod("document").invoke(result);
            List<?> phrases = (List<?>) document.getClass().getMethod("phrases").invoke(document);
            JSONObject aliases = new JSONObject();
            for (Object phrase : phrases) {
                if ("测试姓名".equals(phrase.getClass().getMethod("text").invoke(phrase)))
                    aliases.put((String) phrase.getClass().getMethod("id").invoke(phrase), "csxm");
            }
            local.getJSONObject("settings").put("platform.android.prefix_completion", true)
                .put("platform.android.phrase_aliases", aliases.toString())
                .put("platform.android.keyboard_height_adjustment", 0);
            save(); rebind(); openEditor();
            stage = "phone prefix from real numeric keys";
            tap(key("123"));
            for (String digit : new String[] {"1", "3", "8"}) { stage = "phone digit " + digit; tap(key(digit)); }
            stage = "phone completion candidate";
            completion("13800000000", "1");
            await(field("msime-test-plain").and(node -> equalsText("13800000000", node.getText())));
            if (android.os.Build.VERSION.SDK_INT < 30) {
                // Android 9 的缓存跨新编辑器不会稳定刷新，保留同一编辑器验证旋转与真实上屏。
                stage = "API 28 landscape composition and commit";
                local.getJSONObject("settings").put("platform.android.keyboard_height_adjustment", 109);
                save();
                shell("settings put system accelerometer_rotation 0");
                shell("settings put system user_rotation 1"); SystemClock.sleep(3000);
                rebind(); SystemClock.sleep(8000); type("nihao");
                shell("screencap -p /sdcard/msime-compact-landscape.png");
                shell("input keyevent KEYCODE_SPACE");
                await(field("msime-test-plain").and(node -> equalsText("你好", node.getText())
                    || equalsText("13800000000你好", node.getText())));
                return;
            }
            stage = "identity prefix";
            openEditor();
            tap(key("123"));
            for (String digit : new String[] {"1", "1", "0"}) tap(key(digit));
            completion("11000020000101000X", "1");
            await(field("msime-test-plain").and(node -> equalsText("11000020000101000X", node.getText())));
            stage = "name alias consumes composition";
            openEditor(); type("cs");
            completion("测试姓名", "q");
            await(field("msime-test-plain").and(node -> equalsText("测试姓名", node.getText())));
            type("n");
            await(field("msime-test-plain").and(node -> equalsText("测试姓名n", node.getText())));
            stage = "password excludes personal phrases";
            tap(field("msime-test-password")); type("cs"); SystemClock.sleep(500);
            for (var window : automation.getWindows())
                if (find(window.getRoot(), describedPrefix("前缀补全：")) != null)
                    throw new AssertionError("Personal phrase shown in password field");

            stage = "numeric presets contain familiar rows";
            for (String preset : new String[] {"phone", "chat", "calculator", "row", "custom"}) {
                local.getJSONObject("settings").put("platform.android.number_arrangement", preset)
                    .put("platform.android.custom_digit_order", "9876543210");
                save(); rebind(); openEditor(); tap(key("123"));
                Rect one = bounds("1"), two = bounds("2"), three = bounds("3");
                if (Math.abs(one.top - two.top) > 2 || Math.abs(two.top - three.top) > 2)
                    throw new AssertionError("Numeric row is not aligned: " + preset);
                if (!"custom".equals(preset) && !(one.left < two.left && two.left < three.left))
                    throw new AssertionError("Numeric order is reversed: " + preset);
                tap(key("1")); tap(key("2")); tap(key("3"));
                await(field("msime-test-plain").and(node -> equalsText("123", node.getText())));
            }

            stage = "custom symbols insert configured character";
            local.getJSONObject("settings").put("platform.android.custom_symbol_rows", "@ # $ %\n& * + =");
            save(); rebind(); openEditor(); tap(key("123")); tap(key("#+=")); tap(key("@"));
            await(field("msime-test-plain").and(node -> equalsText("@", node.getText())));

            stage = "landscape constrained height and visible candidates";
            local.getJSONObject("settings").put("platform.android.keyboard_height_adjustment", 109);
            save();
            shell("settings put system accelerometer_rotation 0");
            shell("settings put system user_rotation 1"); SystemClock.sleep(2000);
            rebind(); openEditor(); type("nihao");
            Rect candidateBounds = new Rect();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                await(imeTextContains("你好").and(AccessibilityNodeInfo::isClickable)).getBoundsInScreen(candidateBounds);
            Rect keyboard = new Rect(); await(described("输入法键盘")).getBoundsInScreen(keyboard);
            int screenHeight = automation.getWindows().stream().mapToInt(window -> {
                Rect rectangle = new Rect(); window.getBoundsInScreen(rectangle); return rectangle.bottom;
            }).max().orElse(0);
            if ((android.os.Build.VERSION.SDK_INT >= 30 && (candidateBounds.top < keyboard.top || candidateBounds.bottom > keyboard.bottom))
                    || keyboard.bottom > screenHeight || keyboard.height() > screenHeight * .8)
                throw new AssertionError("Landscape candidate or keyboard escaped height budget");
            shell("screencap -p /sdcard/msime-compact-landscape.png");
            if (android.os.Build.VERSION.SDK_INT >= 30) tap(imeTextContains("你好").and(AccessibilityNodeInfo::isClickable));
            else candidateTouch("q");
            await(field("msime-test-plain").and(node -> equalsText("你好", node.getText())));
            stage = "hide and reopen engine remains usable";
            shell("input keyevent KEYCODE_BACK"); SystemClock.sleep(100); tap(field("msime-test-plain"));
            SystemClock.sleep(1000); type("nihao"); tap(key("空格"));
            await(field("msime-test-plain").and(node -> equalsText("你好你好", node.getText())));
            stage = "portrait recovery";
            shell("settings put system user_rotation 0"); SystemClock.sleep(2000);
            rebind(); openEditor(); type("nihao"); tap(key("空格"));
            await(field("msime-test-plain").and(node -> equalsText("你好", node.getText())));
        } finally {
            shell("settings put system user_rotation 0");
            shell("settings put system accelerometer_rotation 1");
            if (original == null) Files.deleteIfExists(localSettings.toPath()); else publish(localSettings, original);
        }
    }

    private Rect bounds(String keyText) {
        Rect bounds = new Rect(); await(key(keyText)).getBoundsInScreen(bounds); return bounds;
    }
    private void completion(String text, String firstKey) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 30) tap(described("前缀补全：" + text));
        else candidateTouch(firstKey);
    }
    private void candidateTouch(String firstKey) throws Exception {
        // Android 9 的动态候选树可能保留旧子节点，以实测键区位置点第一项，编辑器全文断言负责验收。
        SystemClock.sleep(800);
        int x = Math.round(getTargetContext().getResources().getDisplayMetrics().widthPixels * .15f);
        int y = bounds(firstKey).top - Math.round(32 * getTargetContext().getResources().getDisplayMetrics().density);
        shell("input tap " + x + " " + y); SystemClock.sleep(300);
    }
    private void type(String value) {
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(value.toCharArray());
        if (events == null) throw new AssertionError("Synthetic key mapping unavailable");
        for (KeyEvent event : events) { automation.injectInputEvent(event, true); SystemClock.sleep(100); }
        SystemClock.sleep(300);
    }
    private void save() throws Exception { publish(localSettings, local.toString().getBytes(StandardCharsets.UTF_8)); }
    private static void publish(File file, byte[] bytes) throws Exception {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream output = atomic.startWrite();
        try { output.write(bytes); atomic.finishWrite(output); } catch (Exception error) { atomic.failWrite(output); throw error; }
    }
    private void rebind() throws Exception {
        shell("ime disable app.msime.android/.MSIMEInputService");
        shell("ime enable app.msime.android/.MSIMEInputService");
        shell("ime set app.msime.android/.MSIMEInputService"); SystemClock.sleep(1000);
    }
    private void openEditor() throws Exception {
        shell("am start -W -f 0x10008000 -n app.msime.android.test/app.msime.android.test.EditorActivity"); SystemClock.sleep(900);
        tap(field("msime-test-plain"));
        await(describedPrefix("输入方案：").and(AccessibilityNodeInfo::isEnabled));
        SystemClock.sleep(android.os.Build.VERSION.SDK_INT < 30 ? 2500 : 500);
    }
    private void shell(String command) throws Exception {
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        }
    }
    @Override protected void tap(java.util.function.Predicate<AccessibilityNodeInfo> predicate)
            throws java.util.concurrent.TimeoutException {
        AccessibilityNodeInfo node = await(predicate.and(AccessibilityNodeInfo::isClickable));
        if (!equalsText("app.msime.android", node.getPackageName())) { super.tap(predicate); return; }
        // 某些键帽代理只处理触摸；使用当前可见节点的实测位置点按。
        node.refresh();
        Rect rectangle = new Rect(); node.getBoundsInScreen(rectangle);
        if (rectangle.isEmpty()) throw new AssertionError("Keyboard control has empty bounds");
        try { shell("input tap " + rectangle.centerX() + " " + rectangle.centerY()); }
        catch (Exception error) { throw new AssertionError("Keyboard fixture touch failed", error); }
        SystemClock.sleep(250);
    }
}
