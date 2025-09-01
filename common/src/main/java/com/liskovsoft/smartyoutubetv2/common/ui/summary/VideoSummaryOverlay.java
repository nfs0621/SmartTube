package com.liskovsoft.smartyoutubetv2.common.ui.summary;

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

import com.liskovsoft.smartyoutubetv2.common.R;

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
    public interface OnEmailListener { void onEmail(); }
    private OnEmailListener onEmailListener;
    public void setOnEmailListener(OnEmailListener l) { 
        this.onEmailListener = l; 
        // Show/hide email button based on whether listener is set
        if (emailBtn != null) {
            emailBtn.setVisibility(l != null ? View.VISIBLE : View.GONE);
        }
    }
    public interface OnConfirmListener { void onConfirm(); }
    private OnConfirmListener onConfirmListener;
    public void setOnConfirmListener(OnConfirmListener l) { this.onConfirmListener = l; }

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
        // Apply compact layout if enabled in settings
        try {
            com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(activity);
            boolean compact = gd.isCompactLayout();
            android.view.View panel = root.findViewById(R.id.gemini_panel);
            if (panel != null && panel instanceof android.view.ViewGroup) {
                int outerPad = compact ? dp(32) : dp(48);
                ((android.view.ViewGroup) panel).setPadding(outerPad, outerPad, outerPad, outerPad);
            }
            status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, compact ? 18 : 20);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, compact ? 16 : 18);
            text.setLineSpacing(compact ? dp(4) : dp(6), 1.0f);
            android.widget.TextView hint = root.findViewById(R.id.gemini_hint);
            if (hint != null) hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, compact ? 13 : 14);
        } catch (Throwable ignored) {}

        // Create a custom FrameLayout to intercept key events
        android.widget.FrameLayout customRoot = new android.widget.FrameLayout(activity) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    
                    // Close overlay on back/left/right only; allow Select/Enter to reach focused child
                    if (keyCode == KeyEvent.KEYCODE_BACK || 
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        hide();
                        return true;
                    }
                    // Scroll with up/down
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (emailBtn != null && emailBtn.hasFocus()) {
                            // Move focus back to scrollable text when navigating up from button
                            scroll.requestFocus();
                            return true;
                        }
                        VideoSummaryOverlay.this.scrollBy(-200);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (scroll != null && scroll.canScrollVertically(1)) {
                            VideoSummaryOverlay.this.scrollBy(200);
                            return true;
                        }
                        if (emailBtn != null) {
                            emailBtn.requestFocus();
                            return true;
                        }
                        return false;
                    }
            }
            return super.dispatchKeyEvent(event);
            }
        };

        
        
        // Set layout params and properties on the custom root
        customRoot.setLayoutParams(root.getLayoutParams());
        // Preserve background (full-screen scrim) from inflated layout
        try { customRoot.setBackground(root.getBackground()); } catch (Throwable ignored) {}
        customRoot.setClickable(true);
        customRoot.setFocusable(true);
        customRoot.setFocusableInTouchMode(true);
        ((ViewGroup) customRoot).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        
        // Move all children from root to customRoot
        ViewGroup rootGroup = (ViewGroup) root;
        while (rootGroup.getChildCount() > 0) {
            View child = rootGroup.getChildAt(0);
            rootGroup.removeView(child);
            customRoot.addView(child);
        }
        
        root = customRoot;
        // Wire up Email button
        emailBtn = root.findViewById(R.id.gemini_email_btn);
        if (emailBtn != null) {
            emailBtn.setOnClickListener(v -> { if (onEmailListener != null) onEmailListener.onEmail(); });
            emailBtn.setFocusable(true);
            emailBtn.setFocusableInTouchMode(true);
            // Hide by default - will be shown when listener is set
            emailBtn.setVisibility(View.GONE);
        }

        content.addView(root);
    }

    private void scrollBy(int dy) {
        scroll.smoothScrollBy(0, dy);
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
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
        root.requestFocus(); // Ensure overlay keeps focus for D-pad navigation
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
