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
import android.widget.Button;

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
    private View emailBtn;

    public VideoSummaryOverlay(Activity activity) {
        this.activity = activity;
        ensureInflated();
    }

    private void ensureInflated() {
        if (root != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        root = LayoutInflater.from(activity).inflate(R.layout.overlay_video_summary, content, false);
        // Allow descendants (like the Email button) to receive focus
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        progress = root.findViewById(R.id.gemini_progress);
        status = root.findViewById(R.id.gemini_status);
        text = root.findViewById(R.id.gemini_text);
        scroll = root.findViewById(R.id.gemini_scroll);
        emailBtn = root.findViewById(R.id.gemini_email_btn);

        if (emailBtn != null) {
            emailBtn.setFocusable(true);
            emailBtn.setFocusableInTouchMode(true);
            emailBtn.setOnClickListener(v -> {
                if (onEmailListener != null) onEmailListener.onEmail();
            });
        }

        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    // Let focused child (e.g., Email button) handle Select/Enter
                    return false;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    hide();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (emailBtn != null && root.findFocus() == emailBtn) {
                        // Move focus back to scrollable text when navigating up from button
                        scroll.requestFocus();
                        return true;
                    }
                    scrollBy(-200);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (scroll != null && scroll.canScrollVertically(1)) {
                        scrollBy(200);
                        return true;
                    }
                    if (emailBtn != null) {
                        emailBtn.requestFocus();
                        return true;
                    }
                    return false;
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
        handler.post(() -> {
            scroll.scrollTo(0, 0);
            root.requestFocus();
        });
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

    public interface OnConfirmListener {
        void onConfirm();
    }
    private OnConfirmListener onConfirmListener;
    public void setOnConfirmListener(OnConfirmListener l) { this.onConfirmListener = l; }

    public interface OnEmailListener { void onEmail(); }
    private OnEmailListener onEmailListener;
    public void setOnEmailListener(OnEmailListener l) { this.onEmailListener = l; }

    
}

