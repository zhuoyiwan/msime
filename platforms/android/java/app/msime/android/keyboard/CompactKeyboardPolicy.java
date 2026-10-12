package app.msime.android;

/** 有限窗口优先保留候选，只压缩键区；保存的键盘高度不受影响。 */
public final class CompactKeyboardPolicy {
    private CompactKeyboardPolicy() {}

    public static int heightLimit(int parentHeight, int configurationHeight, boolean landscape) {
        // PanelSurface 会用键盘已量出的高度再以 EXACTLY 测量，父约束不可反复乘比例。
        int budget = configurationHeight > 0
            ? Math.max(1, Math.round(configurationHeight * (landscape ? 0.68f : 0.90f)))
            : Integer.MAX_VALUE;
        return parentHeight > 0 ? Math.min(parentHeight, budget) : budget;
    }

    public static float keyScale(int naturalHeight, int keyHeight, int heightLimit) {
        if (keyHeight <= 0 || naturalHeight <= heightLimit) return 1f;
        int reserved = Math.max(0, naturalHeight - keyHeight);
        return Math.max(0f, Math.min(1f, (float) (heightLimit - reserved) / keyHeight));
    }
}
