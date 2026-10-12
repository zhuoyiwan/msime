package app.msime.android.home;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;

/** 仅供本机试用键盘，不发送文字或调用账号服务。 */
public final class LocalTryoutFragment extends Fragment {
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(24 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding * 2, padding, padding);
        TextView title = new TextView(requireContext());
        title.setText("试用键盘"); title.setTextSize(24); column.addView(title);
        TextView note = new TextView(requireContext());
        note.setText("在下面输入文字，也可复制、粘贴。内容只留在本页。"); column.addView(note);
        EditText input = new EditText(requireContext());
        input.setHint("点此输入或粘贴文字"); input.setMinLines(5); input.setGravity(Gravity.TOP);
        column.addView(input, new LinearLayout.LayoutParams(-1, -2));
        input.requestFocus();
        return column;
    }
}
