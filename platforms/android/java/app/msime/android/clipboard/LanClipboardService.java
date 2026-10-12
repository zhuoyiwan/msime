package app.msime.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** 用户开启同步后保留一个可见的局域网连接服务，不依赖键盘窗口是否打开。 */
public final class LanClipboardService extends Service {
    private static final String CHANNEL = "zhuoyiwan-lan-clipboard";
    private static final String STOP = "app.msime.android.LAN_STOP";
    private static volatile boolean running;

    public static void ensure(Context context) {
        if (running) return;
        try { context.startForegroundService(new Intent(context, LanClipboardService.class)); }
        catch (IllegalStateException | SecurityException ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "局域网剪贴板同步", NotificationManager.IMPORTANCE_LOW));
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, LanClipboardService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(49876, new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("局域网剪贴板同步已开启")
            .setContentText("与已配对设备共享文字 · 点此查看连接状态")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "停止同步", stop).build()).build());
        LanClipboardRuntime.start(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            try { LanClipboardRuntime.get(this).command("enable", "", "", false); } catch (Exception ignored) { }
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            LanClipboardRuntime runtime = LanClipboardRuntime.get(this);
            runtime.command("status", "", "", false);
            if (!runtime.active()) { stopSelf(); return START_NOT_STICKY; }
        } catch (Exception error) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    @Override public void onDestroy() { running = false; super.onDestroy(); }

    @Override public IBinder onBind(Intent intent) { return null; }
}
