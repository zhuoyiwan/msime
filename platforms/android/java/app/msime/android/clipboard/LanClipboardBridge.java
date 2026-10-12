package app.msime.android;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** 系统剪贴板的唯一局域网采集入口；远端写入按标签与文字同时识别，防止回传。 */
final class LanClipboardBridge {
    private final LanClipboardRuntime runtime;
    private final ClipboardManager clipboard;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String remoteLabel = "", remoteText = "", handled = "";
    private final java.util.Set<String> capturing = new java.util.HashSet<>();
    private final java.util.concurrent.atomic.AtomicLong remoteGeneration = new java.util.concurrent.atomic.AtomicLong();

    LanClipboardBridge(Context context, LanClipboardRuntime runtime) {
        this.runtime = runtime;
        clipboard = context.getSystemService(ClipboardManager.class);
        main.post(() -> {
            if (clipboard != null) clipboard.addPrimaryClipChangedListener(this::capture);
        });
    }

    void captureNow() { main.post(this::capture); }

    private void capture() {
        if (!runtime.active() || clipboard == null || !ImePrivacyGate.allowsLanCapture()) return;
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                runtime.captureStatus("未读到文字，请将水杉设为默认输入法后重新复制");
                return;
            }
            ClipDescription description = clip.getDescription();
            if (description.getExtras() != null && description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) return;
            CharSequence raw = clip.getItemAt(0).getText();
            if (raw == null || raw.length() == 0 || raw.length() > 4000) return;
            String text = raw.toString();
            String label = description.getLabel() == null ? "" : description.getLabel().toString();
            if (label.equals(remoteLabel) && text.equals(remoteText)) return;
            String identity = description.getTimestamp() + ":" + label + ":" + LanClipboardProtocol.hexBytes(LanClipboardProtocol.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (identity.equals(handled) || !capturing.add(identity)) return;
            runtime.captureSystem(text, success -> main.post(() -> {
                capturing.remove(identity);
                if (success) handled = identity;
            }));
        } catch (SecurityException error) {
            runtime.captureStatus("系统拒绝读取剪贴板，请将水杉设为默认输入法");
        }
    }

    void received(LanClipboardProtocol.Event event) {
        long generation = remoteGeneration.incrementAndGet();
        // 排队等待主线程时，不能覆盖用户随后在本机新复制的内容。
        final String before;
        try { before = descriptionIdentity(); }
        catch (RuntimeException error) { runtime.pasteStatus("已收到文字，但无法读取系统剪贴板状态"); return; }
        main.post(() -> {
            if (generation != remoteGeneration.get() || !runtime.active() || clipboard == null) return;
            try {
                if (!before.equals(descriptionIdentity())) return;
                remoteLabel = "zhuoyiwan.lan:" + event.id();
                remoteText = event.text();
                clipboard.setPrimaryClip(ClipData.newPlainText(remoteLabel, remoteText));
                runtime.pasteStatus("");
            } catch (RuntimeException error) {
                runtime.pasteStatus("已收到文字，但系统剪贴板写入失败，可从历史中粘贴");
            }
        });
    }

    private String descriptionIdentity() {
        ClipDescription description = clipboard == null ? null : clipboard.getPrimaryClipDescription();
        return description == null ? "" : description.getTimestamp() + ":" + description.getLabel();
    }
}
