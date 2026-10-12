package app.msime.android;

import android.os.Build;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;

/** 语音插入位置检查只向编辑器查询一次，避免三次同步 IPC 拖慢上屏。 */
final class VoiceEditorContext {
    record Value(String before, String selected, String after) {
        EditorContextSnapshot snapshot(InputConnection connection, long revision) {
            return new EditorContextSnapshot(connection, revision, before, selected, after);
        }
        boolean matches(EditorContextSnapshot target, InputConnection connection, long revision) {
            return target != null && target.matches(connection, revision, before, selected, after);
        }
    }

    static Value read(InputConnection connection) {
        if (connection == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                SurroundingText text = connection.getSurroundingText(64, 64, 0);
                return text == null ? null : value(text.getText(), text.getSelectionStart(), text.getSelectionEnd());
            }
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = 128;
            request.hintMaxLines = 3;
            ExtractedText text = connection.getExtractedText(request, 0);
            return text == null ? null : value(text.text, text.selectionStart, text.selectionEnd);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Value value(CharSequence text, int start, int end) {
        if (text == null || start < 0 || end < 0 || start > text.length() || end > text.length()) return null;
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        return new Value(text.subSequence(Math.max(0, left - 64), left).toString(),
            text.subSequence(left, right).toString(),
            text.subSequence(right, Math.min(text.length(), right + 64)).toString());
    }
}
