package app.msime.android.home;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import app.msime.android.InputFeatureToggle;
import app.msime.android.R;
import app.msime.android.core.Telemetry;
import java.util.EnumMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * 隐私：联网功能（用水杉账号翻译候选、匿名使用统计）、本机数据（剪贴板历史）、隐私模式说明和隐私政策链接。
 *
 * <p>三个开关就是 {@link InputFeatureToggle} 里的那三项，标题和说明也取那里，本页是它们在宿主里唯一的位置（实施计划 §2.9）。隐私模式的语义在 Rust 与键盘里（P17）：这里只说明它做什么、不做什么。
 */
public final class PrivacyPage extends DetailPage {
    private static final String POLICY = "https://msime.app/privacy/";
    private static final InputFeatureToggle[] NETWORK = {};
    private static final InputFeatureToggle[] LOCAL = {InputFeatureToggle.CLIPBOARD_HISTORY};

    private final Map<InputFeatureToggle, GroupCard.Row> rows = new EnumMap<>(InputFeatureToggle.class);
    @Nullable private JSONObject preferences;

    @Override protected void buildContent(LinearLayout column, Bundle args) {
        rows.clear();
        String app = getString(R.string.app_name);
        GroupCard intro = GroupCard.add(column, null);
        intro.note(app + "改版使用本机输入与局域网互通，不提供上游账号、匿名注册或云同步。局域网同步开启后，新收到的文字会更新系统剪贴板，并保存在本机历史中。");


        GroupCard local = GroupCard.add(column, "本机数据").withDividers(16);
        for (InputFeatureToggle toggle : LOCAL) addToggle(local, toggle);

        GroupCard incognito = GroupCard.add(column, "隐私模式");
        incognito.note("打开键盘隐私模式后，不学习新词、不记录输入统计，也不保存或同步剪贴板。系统标记为敏感的内容和配对密钥不分享。");

        GroupCard links = GroupCard.add(column, null);
        links.nav("隐私政策", "msime.app", null, this::openPolicy);
        render();
    }

    @Override protected void onBecameVisible() { reload(); }

    private void addToggle(GroupCard group, InputFeatureToggle toggle) {
        GroupCard.Row row = group.toggle(toggle.title(), toggle.description(), toggle.enabledByDefault(),
            value -> save(toggle, value));
        row.setEnabled(false);
        rows.put(toggle, row);
    }

    private void reload() {
        HostTask.run(this, HostStore::loadPreferences, snapshot -> {
            preferences = snapshot == null ? null : snapshot.optJSONObject("preferences");
            render();
        });
    }

    private void render() {
        for (Map.Entry<InputFeatureToggle, GroupCard.Row> entry : rows.entrySet()) {
            InputFeatureToggle toggle = entry.getKey();
            GroupCard.Row row = entry.getValue();
            // 偏好还没读到时开关保持禁用，避免用户在默认值上切换后被读到的真实值覆盖。
            row.setEnabled(preferences != null);
            if (preferences != null) row.setChecked(preferences.optBoolean(toggle.key(), toggle.enabledByDefault()));
        }
    }

    private void save(InputFeatureToggle toggle, boolean value) {
        HostTask.run(this, context -> HostStore.putPreference(context, toggle.key(), value), saved -> {
            if (saved == null) {
                MsToast.show(requireContext(), "没有保存，请重试");
            } else {
                preferences = saved.optJSONObject("preferences");
                // 关闭要当场停止上报并清空待发数据，不能等下次启动才读到偏好。
                if (toggle == InputFeatureToggle.USAGE_REPORTING) Telemetry.setEnabled(requireContext(), value);
            }
            render();
        });
    }

    private void openPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(POLICY)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException absent) {
            MsToast.show(requireContext(), "这台设备上没有可以打开网页的应用");
        }
    }
}
