package app.msime.android;

import android.widget.Button;
import android.widget.LinearLayout;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** 本机常用语的候选入口；点击时重新核对输入框和前缀，不覆盖迟到候选。 */
final class ImePrefixCompletions {
    private final MSIMEInputService s;
    private List<PrefixCompletionPolicy.Entry> entries = List.of();
    private PrefixCompletionPolicy.Match match;
    ImePrefixCompletions(MSIMEInputService s) { this.s = s; }

    void setEntries(CommonPhrasesStore.Document document, String aliases) {
        JSONObject codes;
        try { codes = new JSONObject(aliases); } catch (org.json.JSONException error) { codes = new JSONObject(); }
        java.util.ArrayList<PrefixCompletionPolicy.Entry> next = new java.util.ArrayList<>();
        for (CommonPhrasesStore.Phrase phrase : document.phrases()) {
            if (phrase.own()) next.add(new PrefixCompletionPolicy.Entry(phrase.id(), phrase.text(), codes.optString(phrase.id(), "")));
        }
        entries = List.copyOf(next);
    }

    void clear() { match = null; entries = List.of(); }
    boolean active() { return match != null; }

    void update() { match = currentMatch(); }
    private PrefixCompletionPolicy.Match currentMatch() {
        if (s.connection == null || !s.localSettings.bool(AndroidLocalSettings.PREFIX_COMPLETION)
                || !s.imePrivacyGate.allows(ImePrivacyGate.Record.INPUT_EVENTS)) return null;
        String composition = s.view == null ? "" : app.msime.android.core.InputViewValuePolicy.editingText(s.view);
        if (s.hasEngineComposition() && (composition.isEmpty()
                || !"none".equals(s.view.optString("local_mode", "none")))) return null;
        CharSequence selected = s.connection.getSelectedText(0);
        if (selected != null && selected.length() > 0) return null;
        CharSequence before = composition.isEmpty() ? s.connection.getTextBeforeCursor(PrefixCompletionPolicy.MAX_CONTEXT, 0) : null;
        return PrefixCompletionPolicy.match(entries, composition, before);
    }

    void render(LinearLayout candidates) {
        if (match == null) return;
        for (PrefixCompletionPolicy.Entry entry : match.entries()) {
            PrefixCompletionPolicy.Match shown = match;
            Button button = new KeyboardPressButton(s);
            ViewPolicy.setAllCapsFalse(button);
            button.setText(entry.text());
            button.setContentDescription("前缀补全：" + entry.text());
            KeyboardTypographyPolicy.setKeyTextSize(button, s.candidateFontSize);
            s.imeStyler.styleButton(button, false);
            ViewPolicy.bindClick(button, () -> use(shown, entry));
            candidates.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void use(PrefixCompletionPolicy.Match shown, PrefixCompletionPolicy.Entry entry) {
        PrefixCompletionPolicy.Match now = currentMatch();
        if (now == null || !now.prefix().equals(shown.prefix()) || now.composing() != shown.composing()
                || !now.entries().contains(entry)) { s.render(); return; }
        android.view.inputmethod.InputConnection target = s.connection;
        target.beginBatchEdit();
        try {
            if (shown.composing()) s.discardComposition();
            else if (!s.deleteBeforeCursor(shown.prefix().length())) return;
            if (s.connection == target) s.commitText(entry.text());
        } finally { target.endBatchEdit(); }
        match = null;
        s.render();
    }
}
