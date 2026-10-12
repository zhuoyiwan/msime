package app.msime.android.test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 检查实际本机页主题入口，并在宿主队列被占用时验证词库页仍然可见。 */
public final class LocalExperienceDeviceSmoke extends DeviceSmoke {
    @Override protected String successDescription() { return "visible theme entrance, dark mode, blocked lexicon loading and recovery"; }

    @Override protected void runChecks() throws Exception {
        java.io.File file = new java.io.File(getTargetContext().getFilesDir(), "bootstrap/state/android-settings.json");
        byte[] previous = file.exists() ? java.nio.file.Files.readAllBytes(file.toPath()) : null;
        try {
            checkExperience();
        } finally {
            if (previous == null) java.nio.file.Files.deleteIfExists(file.toPath());
            else {
                android.util.AtomicFile atomic = new android.util.AtomicFile(file);
                var stream = atomic.startWrite();
                try { stream.write(previous); atomic.finishWrite(stream); }
                catch (Exception error) { atomic.failWrite(stream); throw error; }
            }
        }
    }

    private void checkExperience() throws Exception {
        Context context = getTargetContext();
        context.getSharedPreferences("msime_onboarding_v1", Context.MODE_PRIVATE).edit().putBoolean("seen", true).commit();
        Class<?> home = Class.forName("app.msime.android.home.HomeActivity", true, context.getClassLoader());
        Activity activity = startActivitySync(new Intent(context, home)
            .putExtra("msime_open_tab", 3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        stage = "actual local tools theme entrance";
        awaitLabel(activity, "应用主题");
        tap(node -> equalsText("应用主题", node.getText()));
        tap(node -> equalsText("深色", node.getText()));
        SystemClock.sleep(1500);
        stage = "dark mode survives restart";
        Activity first = activity;
        runOnMainSync(first::finish);
        activity = startActivitySync(new Intent(context, home)
            .putExtra("msime_open_tab", 3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        if ((activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES) throw new AssertionError("Dark mode did not persist");
        Activity previous = activity;
        runOnMainSync(previous::finish);
        Class<?> tasks = Class.forName("app.msime.android.home.HostTask", true, context.getClassLoader());
        var field = tasks.getDeclaredField("WORKER");
        field.setAccessible(true);
        ExecutorService worker = (ExecutorService) field.get(null);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> {
            blocked.countDown();
            try { release.await(30, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        if (!blocked.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker did not block");
        try {
            stage = "lexicon visible while worker is occupied";
            activity = startActivitySync(new Intent(context, home).putExtra("msime_open_page", "LEXICON")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitLabel(activity, "正在读取本机词库…");
            awaitLabel(activity, "新建词库");
            awaitLabel(activity, "刷新词库");
            release.countDown();
            stage = "lexicon read completes";
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (hasLabel(activity, "正在读取本机词库…") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
            if (hasLabel(activity, "正在读取本机词库…")) throw new AssertionError("Lexicon stayed loading");
            awaitLabel(activity, "拼音词库");
        } finally {
            release.countDown();
            Activity last = activity;
            runOnMainSync(last::finish);
        }
    }
    private void awaitLabel(Activity activity, String text) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (!hasLabel(activity, text) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        if (!hasLabel(activity, text)) throw new AssertionError("Missing label: " + text);
    }
    private boolean hasLabel(Activity activity, String text) {
        AtomicBoolean found = new AtomicBoolean();
        runOnMainSync(() -> found.set(contains(activity.getWindow().getDecorView(), text)));
        return found.get();
    }
    private static boolean contains(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return true;
        if (view instanceof ViewGroup group) for (int index = 0; index < group.getChildCount(); index++) {
            if (contains(group.getChildAt(index), text)) return true;
        }
        return false;
    }
}
