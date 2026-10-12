package app.msime.android;

/** Guards a delayed input-view refresh against a stale editor or active composition. */
public final class InputViewRefreshPolicy {
    private InputViewRefreshPolicy() {}

    /** 冷启动期间窗口临时收起会撤销排队任务，再弹出时必须补建会话。 */
    public static boolean resumeStartup(boolean hasSession, boolean pending, boolean connected,
                                        boolean engineEditor, boolean hasOptions) {
        return !hasSession && !pending && connected && engineEditor && hasOptions;
    }

    public static boolean shouldRefresh(
            long expectedGeneration,
            long currentGeneration,
            Object expectedConnection,
            Object currentConnection,
            boolean viewValid,
            boolean hasComposition) {
        return expectedConnection != null
            && currentConnection != null
            && expectedGeneration == currentGeneration
            && expectedConnection == currentConnection
            && viewValid
            && !hasComposition;
    }
}
