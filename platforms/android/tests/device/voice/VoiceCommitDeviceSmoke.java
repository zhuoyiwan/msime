package app.msime.android.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

/** 专用模拟器验证系统最终结果直接进入真实输入框，不提交中间结果。 */
public final class VoiceCommitDeviceSmoke extends DeviceSmoke {
    @Override protected String successDescription() { return "system final result commits once within 1 second; partial is not committed"; }

    @SuppressWarnings("deprecation")
    @Override protected void runChecks() throws Exception {
        Context context = getTargetContext();
        File root = context.getFilesDir();
        JSONObject options = new JSONObject(new String(Files.readAllBytes(new File(root, "runtime-options.json").toPath()), StandardCharsets.UTF_8));
        File preferences = new File(options.getString("preferences_directory"), "preferences.json");
        byte[] previous = Files.readAllBytes(preferences.toPath());
        JSONObject snapshot = new JSONObject(new String(previous, StandardCharsets.UTF_8));
        JSONObject values = snapshot.getJSONObject("preferences");
        values.put("scheme", "quanpin").put("touch_keyboard_layout", "twenty_six_key");
        values.put("voice_input", new JSONObject().put("enabled", true).put("language", "auto").put("asr_provider", "system"));
        File local = new File(root, "bootstrap/state/android-settings.json");
        byte[] savedLocal = local.exists() ? Files.readAllBytes(local.toPath()) : null;
        JSONObject localDoc = local.exists() ? new JSONObject(new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8))
            : new JSONObject().put("version", 1).put("settings", new JSONObject());
        localDoc.getJSONObject("settings").put("platform.android.space_voice", true)
            .put("platform.android.voice_quick_commit", true);
        AtomicLong sent = new AtomicLong();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context owner, Intent event) { sent.set(event.getLongExtra("sent", 0)); }
        };
        String oldService = shell("settings get secure voice_recognition_service").trim();
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver,
            new IntentFilter(FixtureRecognitionService.EVENT), Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, new IntentFilter(FixtureRecognitionService.EVENT));
        try {
            publish(preferences, snapshot.put("revision", snapshot.getLong("revision") + 1).toString().getBytes(StandardCharsets.UTF_8));
            publish(local, localDoc.toString().getBytes(StandardCharsets.UTF_8));
            shell("settings put secure voice_recognition_service app.msime.android.test/." +
                (Build.VERSION.SDK_INT < 31 ? "FixtureLegacyRecognitionService" : "FixtureRecognitionService"));
            shell("pm grant app.msime.android android.permission.RECORD_AUDIO");
            shell("pm grant app.msime.android.test android.permission.RECORD_AUDIO");
            shell("ime disable app.msime.android/.MSIMEInputService");
            shell("ime enable app.msime.android/.MSIMEInputService");
            shell("ime set app.msime.android/.MSIMEInputService");
            shell("am start -W -f 0x10008000 -n app.msime.android.test/.EditorActivity");
            tap(field("msime-test-plain"));
            stage = "start system recognizer in keyboard";
            Rect bounds = new Rect();
            automation.waitForIdle(500, 5000);
            awaitStableBounds(key("空格")).getBoundsInScreen(bounds);
            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            automation.injectInputEvent(down, true);
            SystemClock.sleep(900);
            MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            automation.injectInputEvent(up, true);
            down.recycle(); up.recycle();
            stage = "final result directly in editor";
            await(field("msime-test-plain").and(node -> equalsText(FixtureRecognitionService.FINAL, node.getText())));
            long elapsed = SystemClock.uptimeMillis() - sent.get();
            if (sent.get() == 0 || elapsed > 1000) throw new AssertionError("Final commit exceeded 1 second: " + elapsed);
            SystemClock.sleep(1800);
            await(field("msime-test-plain").and(node -> equalsText(FixtureRecognitionService.FINAL, node.getText())));
        } finally {
            context.unregisterReceiver(receiver);
            if (oldService.equals("null") || oldService.isEmpty()) shell("settings delete secure voice_recognition_service");
            else shell("settings put secure voice_recognition_service " + oldService);
            publish(preferences, previous);
            if (savedLocal == null) Files.deleteIfExists(local.toPath()); else publish(local, savedLocal);
        }
    }

    private static void publish(File file, byte[] bytes) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        var stream = atomic.startWrite();
        try { stream.write(bytes); atomic.finishWrite(stream); }
        catch (Exception error) { atomic.failWrite(stream); throw error; }
    }
    private String shell(String command) throws Exception {
        try (var stream = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            var output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
