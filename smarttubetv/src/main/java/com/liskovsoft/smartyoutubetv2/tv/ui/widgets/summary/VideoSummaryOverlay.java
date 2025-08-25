package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.summary;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

public class VideoSummaryOverlay {
    private final Activity activity;
    private final Handler handler = new Handler();
    private View root;
    private ProgressBar progress;
    private TextView status;
    private TextView text;
    private ScrollView scroll;
    private View previousFocus;

    public VideoSummaryOverlay(Activity activity) {
        this.activity = activity;
        ensureInflated();
    }

    private void ensureInflated() {
        if (root != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        root = LayoutInflater.from(activity).inflate(R.layout.overlay_video_summary, content, false);
        progress = root.findViewById(R.id.gemini_progress);
        status = root.findViewById(R.id.gemini_status);
        text = root.findViewById(R.id.gemini_text);
        scroll = root.findViewById(R.id.gemini_scroll);

        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                hide();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                scrollBy(-200);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                scrollBy(200);
                return true;
            }
            return false;
        });

        content.addView(root);
    }

    private void scrollBy(int dy) {
        scroll.smoothScrollBy(0, dy);
    }

    public void showLoading(CharSequence workingText) {
        ensureInflated();
        previousFocus = activity.getCurrentFocus();
        root.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        status.setText(workingText != null ? workingText : "Summarizing...");
        text.setText("");
        root.requestFocus();
    }

    public void showText(CharSequence title, CharSequence body) {
        ensureInflated();
        progress.setVisibility(View.GONE);
        status.setText(title);
        text.setText(body);
        handler.post(() -> scroll.scrollTo(0, 0));
    }

    public void hide() {
        if (root != null) {
            root.setVisibility(View.GONE);
            if (previousFocus != null) previousFocus.requestFocus();
        }
    }

    public boolean isVisible() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }
}

