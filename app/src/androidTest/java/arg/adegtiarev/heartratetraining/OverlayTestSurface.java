package arg.adegtiarev.heartratetraining;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;

/** Runs in the test APK's own process with only Android framework classes. */
public final class OverlayTestSurface extends Activity {
    private int taps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 31 && getIntent().getBooleanExtra("suppress", false)) {
            getWindow().setHideOverlayWindows(true);
        }
        Button button = new Button(this);
        button.setText("Map control 0");
        button.setOnClickListener(view -> button.setText("Map control " + ++taps));
        FrameLayout content = new FrameLayout(this);
        content.addView(button, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(content);
    }
}
