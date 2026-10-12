package app.msime.android.test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** 专用模拟器中验证真实软件 activity 的主题、设备主题与键盘解析互不覆盖。 */
public final class IndependentThemeDeviceSmoke extends DeviceSmoke {
    @Override protected String successDescription() { return "independent app/keyboard modes and activity restart"; }

    @Override protected void runChecks() throws Exception {
        Context context = getTargetContext();
        context.getSharedPreferences("msime_onboarding_v1", Context.MODE_PRIVATE).edit().putBoolean("seen", true).commit();
        File local = new File(context.getFilesDir(), "bootstrap/state/android-settings.json");
        byte[] previous = local.exists() ? Files.readAllBytes(local.toPath()) : null;
        JSONObject options = new JSONObject(new String(Files.readAllBytes(new File(context.getFilesDir(), "runtime-options.json").toPath()), StandardCharsets.UTF_8));
        File preferencesFile = new File(options.getString("preferences_directory"), "preferences.json");
        byte[] savedPreferences = Files.readAllBytes(preferencesFile.toPath());
        ClassLoader loader = context.getClassLoader();
        Class<?> home = Class.forName("app.msime.android.home.HomeActivity", true, loader);
        Class<?> store = Class.forName("app.msime.android.home.HostStore", true, loader);
        Activity activity = null;
        try {
            boolean systemDark = (Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            for (String mode : new String[] {"dark", "light", "system"}) {
                stage = "software mode " + mode;
                JSONObject doc = local.exists()
                    ? new JSONObject(new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8))
                    : new JSONObject().put("version", 1).put("settings", new JSONObject());
                doc.getJSONObject("settings").put("platform.android.app_color_mode", mode);
                publish(local, doc.toString().getBytes(StandardCharsets.UTF_8));
                Intent intent = new Intent(context, home).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity = startActivitySync(intent);
                waitForIdleSync();
                boolean actualDark = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                boolean expectedDark = "dark".equals(mode) || "system".equals(mode) && systemDark;
                if (actualDark != expectedDark) throw new AssertionError("Software mode did not survive activity startup");
                for (String keyboard : new String[] {"light", "dark", "system"}) {
                    stage = "software " + mode + ", keyboard " + keyboard;
                    JSONObject preferences = new JSONObject().put("theme", "dark")
                        .put("screen_keyboard_theme", "light").put("global_theme", "system");
                    Object skin = store.getMethod("keyboardSkin", JSONObject.class, boolean.class,
                        Class.forName("app.msime.android.AppThemePalette$Seed", true, loader), String.class)
                        .invoke(null, preferences, systemDark, null, keyboard);
                    boolean keyboardDark = (boolean) skin.getClass().getMethod("dark").invoke(skin);
                    boolean expectedKeyboard = "dark".equals(keyboard) || "system".equals(keyboard) && systemDark;
                    if (keyboardDark != expectedKeyboard) throw new AssertionError("Software mode leaked into keyboard preview");
                    if (!"dark".equals(preferences.getString("theme")) || !"light".equals(preferences.getString("screen_keyboard_theme")))
                        throw new AssertionError("Keyboard preview changed shared defaults");
                    boolean deviceDark = (Resources.getSystem().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    if (deviceDark != systemDark) throw new AssertionError("Software override changed device mode source");
                }
                Activity opened = activity;
                runOnMainSync(opened::finish);
                waitForIdleSync();
                activity = null;
            }
            JSONObject preferencesDocument = new JSONObject(new String(savedPreferences, StandardCharsets.UTF_8));
            preferencesDocument.getJSONObject("preferences").put("theme", "light")
                .put("screen_keyboard_theme", "light").put("global_theme", "system");
            preferencesDocument.put("revision", preferencesDocument.getLong("revision") + 1);
            publish(preferencesFile, preferencesDocument.toString().getBytes(StandardCharsets.UTF_8));
            for (String keyboard : new String[] {"dark", "light"}) {
                stage = "real IME keyboard " + keyboard;
                JSONObject document = new JSONObject(new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8));
                document.getJSONObject("settings").put("platform.android.keyboard_color_mode", keyboard);
                publish(local, document.toString().getBytes(StandardCharsets.UTF_8));
                shell("ime disable app.msime.android/.MSIMEInputService");
                shell("ime enable app.msime.android/.MSIMEInputService");
                shell("ime set app.msime.android/.MSIMEInputService");
                shell("am start -W -n app.msime.android.test/app.msime.android.test.EditorActivity");
                tap(field("msime-test-plain"));
                Rect bounds = new Rect();
                await(key("n")).getBoundsInScreen(bounds);
                SystemClock.sleep(1000);
                Bitmap screenshot = automation.takeScreenshot();
                if (screenshot == null) throw new AssertionError("Keyboard screenshot unavailable");
                int pixel = screenshot.getPixel(bounds.centerX(), bounds.top + bounds.height() / 4);
                screenshot.recycle();
                int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                if ("dark".equals(keyboard) ? brightness >= 140 : brightness < 140)
                    throw new AssertionError("Independent mode did not reach the visible IME keyboard");
            }
        } finally {
            if (activity != null) { Activity opened = activity; runOnMainSync(opened::finish); }
            if (previous == null) Files.deleteIfExists(local.toPath()); else publish(local, previous);
            publish(preferencesFile, savedPreferences);
        }
    }

    private void shell(String command) throws Exception {
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
            byte[] buffer = new byte[1024]; while (input.read(buffer) != -1) { }
        }
    }

    private static void publish(File file, byte[] bytes) throws Exception {
        AtomicFile target = new AtomicFile(file);
        FileOutputStream stream = target.startWrite();
        try { stream.write(bytes); target.finishWrite(stream); }
        catch (Exception error) { target.failWrite(stream); throw error; }
    }
}
