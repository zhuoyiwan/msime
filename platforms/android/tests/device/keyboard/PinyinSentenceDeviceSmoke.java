package app.msime.android.test;

import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** 完整内置词库上验证长句末尾补全与句中纠错，选择后消费所有按键。 */
public final class PinyinSentenceDeviceSmoke extends DeviceSmoke {
    @Override protected String successDescription() { return "long sentence tail, transposition, neighbor, missing/extra letters and complete commit"; }
    @Override protected void runChecks() throws Exception {
        File root = getTargetContext().getFilesDir();
        JSONObject options = new JSONObject(new String(Files.readAllBytes(new File(root, "runtime-options.json").toPath()), StandardCharsets.UTF_8));
        File preferences = new File(options.getString("preferences_directory"), "preferences.json");
        byte[] previous = Files.readAllBytes(preferences.toPath());
        JSONObject snapshot = new JSONObject(new String(previous, StandardCharsets.UTF_8));
        JSONObject values = snapshot.getJSONObject("preferences");
        values.put("scheme", "quanpin").put("touch_keyboard_layout", "twenty_six_key").put("learning", false)
            .put("candidate_page_size", 6).put("quanpin", new JSONObject()
                .put("autocorrect_transposition", true).put("autocorrect_neighbor", true));
        try {
            publish(preferences, snapshot.put("revision", snapshot.getLong("revision") + 1).toString().getBytes(StandardCharsets.UTF_8));
            shell("ime disable app.msime.android/.MSIMEInputService");
            shell("ime enable app.msime.android/.MSIMEInputService");
            shell("ime set app.msime.android/.MSIMEInputService");
            android.os.SystemClock.sleep(500);
            for (String raw : new String[] {"woxiangqushangk", "woxiangqusahngk", "woxiangqushabgk", "woxiangqushanngk", "woxiangqushngk", "wixiabgqusahngk", "wixabgqusahngke"}) {
                if (android.os.Build.VERSION.SDK_INT < 30) {
                    checkNumberSelection(raw);
                    continue;
                }
                stage = "candidate " + raw;
                shell("am start -W -f 0x10008000 -n app.msime.android.test/.EditorActivity");
                tap(field("msime-test-plain"));
                StringBuilder typed = new StringBuilder();
                for (char letter : raw.toCharArray()) {
                    tap(key(String.valueOf(letter)));
                    typed.append(letter);
                    String expected = typed.toString();
                    stage = "composition " + expected;
                    await(field("msime-test-plain").and(node -> equalsText(expected, node.getText())));
                }
                stage = "candidate " + raw;
                awaitAny(imeTextContains("我想去上课"));
                stage = "complete commit " + raw;
                tap(imeTextContains("我想去上课"));
                await(field("msime-test-plain").and(node -> equalsText("我想去上课", node.getText())));
            }
        } finally { publish(preferences, previous); }
    }
    // API 28 的候选浮层不在 UiAutomation 树里；按第一页 1–6 号选词并核对真实编辑器。
    private void checkNumberSelection(String raw) throws Exception {
        for (int slot = 0; slot < 6; slot++) {
            stage = "API28 default-page selection " + raw + " slot " + (slot + 1);
            shell("am start -W -f 0x10008000 -n app.msime.android.test/.EditorActivity");
            tap(field("msime-test-plain"));
            for (char letter : raw.toCharArray()) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                awaitStableBounds(key(String.valueOf(letter))).getBoundsInScreen(bounds);
                shell("input tap " + bounds.centerX() + " " + bounds.centerY());
            }
            await(field("msime-test-plain").and(node -> equalsText(raw, node.getText())));
            shell("input keyevent " + (android.view.KeyEvent.KEYCODE_1 + slot));
            android.os.SystemClock.sleep(400);
            for (var window : automation.getWindows()) {
                if (find(window.getRoot(), field("msime-test-plain").and(node -> equalsText("我想去上课", node.getText()))) != null) return;
            }
        }
        throw new AssertionError("No first-page number committed the complete target sentence");
    }
    private static void publish(File file, byte[] bytes) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        var stream = atomic.startWrite();
        try { stream.write(bytes); atomic.finishWrite(stream); }
        catch (Exception error) { atomic.failWrite(stream); throw error; }
    }
    private void shell(String command) throws Exception {
        try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            byte[] buffer = new byte[4096];
            while (stream.read(buffer) != -1) { }
        }
    }
}
