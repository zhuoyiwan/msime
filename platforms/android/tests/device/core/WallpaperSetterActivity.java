package app.msime.android.test;

import android.app.Activity;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.os.Bundle;

/** 只在专用模拟器测试包中设置合成壁纸，产品不申请设置壁纸权限。 */
public final class WallpaperSetterActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(getIntent().getIntExtra("seed", 0xFFBEA0BF));
        try { WallpaperManager.getInstance(this).setBitmap(bitmap); }
        catch (java.io.IOException error) { throw new IllegalStateException("Synthetic wallpaper unavailable", error); }
        finally { bitmap.recycle(); finish(); }
    }
}
