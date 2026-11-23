package top.niunaijun.blackbox.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Displays a fixed label above each cloned TikTok activity. */
public final class CloneStaticTextOverlay {
    private static final String TIKTOK_PACKAGE = "com.zhiliaoapp.musically";
    private static final String TEXT = "45943.454.45";
    private static final String VIEW_TAG = "clony_static_text_overlay";
    private static final int HEIGHT_DP = 38;

    private CloneStaticTextOverlay() {
    }

    public static void attach(Activity activity) {
        String packageName = BActivityThread.getAppPackageName();
        if (packageName == null || !(TIKTOK_PACKAGE.equals(packageName)
                || packageName.startsWith(TIKTOK_PACKAGE + "."))) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) {
            return;
        }

        FrameLayout contentFrame = (FrameLayout) content;
        for (int i = 0; i < contentFrame.getChildCount(); i++) {
            if (VIEW_TAG.equals(contentFrame.getChildAt(i).getTag())) {
                return;
            }
        }

        TextView label = new TextView(activity);
        label.setTag(VIEW_TAG);
        label.setText(TEXT);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setFocusable(false);
        label.setClickable(false);
        label.setBackgroundColor(Color.argb(220, 0, 0, 0));

        int height = (int) (HEIGHT_DP * activity.getResources().getDisplayMetrics().density + 0.5f);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                Gravity.TOP
        );
        contentFrame.addView(label, layoutParams);
        label.bringToFront();
    }
}
