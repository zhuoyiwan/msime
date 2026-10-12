package app.msime.android.home;

import android.os.Bundle;
import android.widget.LinearLayout;

/** 改版的本机工具页，没有账号、登录或云端数据入口。 */
public final class LocalToolsPage extends DetailPage {
    @Override protected CharSequence title() { return "本机"; }
    @Override protected void buildContent(LinearLayout column, Bundle args) {
        GroupCard intro = GroupCard.add(column, null);
        intro.note("zhuoyiwan 改版 · 输入与数据保存在本机，设备之间通过局域网配对互通。");
        GroupCard data = GroupCard.add(column, "本地数据");
        data.nav("前缀补全", "姓名、号码与自定义缩写", null, () -> open(PageId.PREFIX_COMPLETION));
        data.nav("剪贴板历史", "查看、搜索和管理复制过的文字", null, () -> open(PageId.CLIPBOARD_SEARCH));
        data.nav("隐私", "剪贴板历史与隐私模式", null, () -> open(PageId.PRIVACY));
        data.nav("备份与恢复", "导出到你选择的位置", null, () -> open(PageId.BACKUP));
        GroupCard help = GroupCard.add(column, "帮助");
        help.nav("使用帮助", null, null, () -> open(PageId.HELP));
        help.nav("关于", "版本与开源许可", null, () -> open(PageId.ABOUT));
    }
    private void open(PageId page) { SettingsNavigator.open(requireContext(), page, null); }
}
