package app.msime.android.home;

import android.os.Bundle;
import android.widget.LinearLayout;
import app.msime.android.AndroidLocalSettings;
import app.msime.android.CommonPhrasesStore;
import app.msime.android.PrefixCompletionPolicy;
import java.util.Map;
import org.json.JSONObject;

/** 使用已有常用语存储，别名只留本机，不读取或自动收集通讯录。 */
public final class PrefixCompletionPage extends DetailPage {
    private LinearLayout column;
    private record State(CommonPhrasesStore.Result phrases, AndroidLocalSettings.Snapshot local) {}
    @Override protected void buildContent(LinearLayout target, Bundle args) { column = target; reload(); }
    @Override public void onDestroyView() { column = null; super.onDestroyView(); }
    private void reload() {
        HostTask.run(this, context -> new State(CommonPhrasesStore.load(context), AndroidLocalSettings.load(context)), this::render);
    }
    private void render(State state) {
        if (column == null || state == null) return;
        column.removeAllViews();
        GroupCard options = GroupCard.add(column, "输入前缀，点击候选补全");
        options.toggle("前缀补全", "号码至少输入 3 位；姓名可用正文开头或字母缩写。密码框、隐私模式中不显示。",
            state.local().bool(AndroidLocalSettings.PREFIX_COMPLETION),
            value -> KeyboardSheets.saveLocal(this, AndroidLocalSettings.PREFIX_COMPLETION, value, this::reload, this::reload));
        if (state.phrases() == null || !state.phrases().ok()) { options.note("读取词条失败，请稍后重试。"); return; }
        JSONObject aliases = aliases(state.local().text(AndroidLocalSettings.PHRASE_ALIASES));
        GroupCard list = GroupCard.add(column, "本机词条");
        for (CommonPhrasesStore.Phrase phrase : state.phrases().document().phrases()) {
            if (!phrase.own()) continue;
            String alias = aliases.optString(phrase.id(), "");
            list.nav(phrase.text(), alias.isEmpty() ? "按正文开头补全；点按设置缩写" : "缩写：" + alias,
                null, () -> editAlias(phrase, alias));
        }
        GroupCard actions = GroupCard.add(column, null);
        actions.button("添加补全词条", null, "添加", this::add);
        actions.nav("修改或删除词条", "与本机常用语共用数据", null,
            () -> SettingsNavigator.open(requireContext(), PageId.PHRASES, null));
        actions.note("只使用手动保存的词条，不会自动学习身份证或电话号码。数据只留本机。");
    }
    private void add() {
        InputDialog dialog = new InputDialog(requireContext(), "添加补全词条", "填入完整姓名、手机号、身份证号等；保存后可设置字母缩写。");
        dialog.addField("完整内容", "", 0);
        dialog.setValidator(values -> CommonPhrasesStore.validText(values.get(0)) && values.get(0).length() <= 200
            && values.get(0).indexOf('\n') < 0);
        dialog.setPrimary("保存", values -> HostTask.run(this, context -> CommonPhrasesStore.add(context, values.get(0)), result -> {
            if (result == null || !result.ok()) MsToast.show(requireContext(), "保存失败或词条已存在");
            reload();
        }));
        dialog.show();
    }
    private void editAlias(CommonPhrasesStore.Phrase phrase, String current) {
        InputDialog dialog = new InputDialog(requireContext(), "补全缩写", "可填小写字母或数字，最多 32 位，例如姓名缩写；留空仅匹配正文前缀。");
        dialog.addField("缩写", current, 0);
        dialog.setValidator(values -> PrefixCompletionPolicy.validAlias(values.get(0)));
        dialog.setPrimary("保存", values -> HostTask.run(this, context -> {
            try {
                JSONObject codes = aliases(AndroidLocalSettings.load(context).text(AndroidLocalSettings.PHRASE_ALIASES));
                if (values.get(0).isEmpty()) codes.remove(phrase.id()); else codes.put(phrase.id(), values.get(0));
                if (codes.toString().length() > 8192) return false;
                AndroidLocalSettings.update(context, Map.of(AndroidLocalSettings.PHRASE_ALIASES, codes.toString()));
                return true;
            } catch (java.io.IOException | org.json.JSONException error) { return false; }
        }, saved -> { if (!Boolean.TRUE.equals(saved)) MsToast.show(requireContext(), "缩写保存失败"); reload(); }));
        dialog.show();
    }
    private static JSONObject aliases(String text) {
        try { return new JSONObject(text); } catch (org.json.JSONException error) { return new JSONObject(); }
    }
}
