package app.msime.android.test;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.List;

/** 固定中间结果与最终结果，用真实系统回调检查上屏时序，不录音。 */
public class FixtureRecognitionService extends RecognitionService {
    static final String EVENT = "app.msime.android.test.VOICE_FINAL";
    static final String FINAL = "语音立即上屏。";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback active;

    @Override protected void onStartListening(Intent intent, Callback callback) {
        active = callback;
        try { callback.readyForSpeech(new Bundle()); } catch (android.os.RemoteException error) { active = null; }
        handler.postDelayed(() -> {
            if (active != callback) return;
            try { callback.partialResults(result("识别中的文字")); }
            catch (android.os.RemoteException error) { active = null; }
        }, 200);
        handler.postDelayed(() -> complete(callback), 900);
    }

    private void complete(Callback callback) {
        if (active != callback) return;
        active = null;
        sendBroadcast(new Intent(EVENT).setPackage("app.msime.android")
            .putExtra("sent", SystemClock.uptimeMillis()));
        try { callback.endOfSpeech(); callback.results(result(FINAL)); }
        catch (android.os.RemoteException ignored) { }
    }

    @Override protected void onStopListening(Callback callback) { complete(callback); }
    @Override protected void onCancel(Callback callback) { active = null; handler.removeCallbacksAndMessages(null); }

    private static Bundle result(String text) {
        Bundle result = new Bundle();
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(List.of(text)));
        return result;
    }
}
