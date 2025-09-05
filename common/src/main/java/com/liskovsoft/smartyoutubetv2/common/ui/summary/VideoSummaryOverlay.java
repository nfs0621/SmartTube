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
    private android.widget.Button listenBtn;
    private TextView footerMeta;
    private boolean firstContentShown;
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
        if (scroll != null) {
            scroll.setFocusable(true);
            scroll.setFocusableInTouchMode(true);
        }
        footerMeta = root.findViewById(R.id.gemini_footer_meta);
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
                    View currentFocus = findFocus();

                    // Back/Escape closes overlay
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        hide();
                        return true;
                    }
                    // Play/Pause toggles TTS
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                        toggleSpeak();
                        return true;
                    }
                    // Up: from buttons return to text; otherwise scroll up
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if ((emailBtn != null && emailBtn.hasFocus()) || (listenBtn != null && listenBtn.hasFocus())) {
                            if (scroll != null) scroll.requestFocus();
                            return true;
                        }
                        VideoSummaryOverlay.this.scrollBy(-200);
                        return true;
                    }
                    // Down: scroll if possible, else Push -> Listen
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (scroll != null && scroll.canScrollVertically(1)) {
                            VideoSummaryOverlay.this.scrollBy(200);
                            return true;
                        }
                        if (currentFocus == emailBtn && listenBtn != null) {
                            listenBtn.requestFocus();
                            return true;
                        }
                        if (emailBtn != null) {
                            emailBtn.requestFocus();
                            return true;
                        } else if (listenBtn != null) {
                            listenBtn.requestFocus();
                            return true;
                        }
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
        // Wire up Listen and Email buttons
        listenBtn = root.findViewById(com.liskovsoft.smartyoutubetv2.common.R.id.gemini_listen_btn);
        if (listenBtn != null) {
            listenBtn.setOnClickListener(v -> toggleSpeak());
            listenBtn.setFocusable(true);
            listenBtn.setFocusableInTouchMode(true);
        }
        emailBtn = root.findViewById(R.id.gemini_email_btn);
        if (emailBtn != null) {
            emailBtn.setOnClickListener(v -> { if (onEmailListener != null) onEmailListener.onEmail(); });
            emailBtn.setFocusable(true);
            emailBtn.setFocusableInTouchMode(true);
            // Hide by default - will be shown when listener is set
            emailBtn.setVisibility(View.GONE);
        }

        content.addView(root);

        // Keep focus on overlay while visible (avoid leaking focus to underlying views)
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (!isVisible()) return;
            // If focus moves outside of overlay (or is null), pull it back to the overlay
            if (newFocus == null || !isDescendant((ViewGroup) root, newFocus)) {
                root.requestFocus();
            }
        });
    }

    private void scrollBy(int dy) {
        scroll.smoothScrollBy(0, dy);
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    public void showLoading(CharSequence workingText) {
        ensureInflated();
        firstContentShown = false;
        previousFocus = activity.getCurrentFocus();
        root.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        status.setText(workingText != null ? workingText : "Summarizing...");
        text.setText("");
        if (scroll != null) {
            scroll.requestFocus();
        } else {
            root.requestFocus();
        }
    }

    public void showText(CharSequence title, CharSequence body) {
        ensureInflated();
        progress.setVisibility(View.GONE);
        status.setText(title);
        int prevY = scroll != null ? scroll.getScrollY() : 0;
        text.setText(body);
        handler.post(() -> {
            if (!firstContentShown) {
                // On first render, start at top
                scroll.scrollTo(0, 0);
                firstContentShown = true;
                // Auto-speak if enabled (opt-in)
                try {
                    if (com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(activity).isTtsSpeakOnOpen()) {
                        handler.postDelayed(this::toggleSpeak, 350);
                    }
                } catch (Throwable ignored) {}
            } else if (prevY > 0) {
                // Preserve user position on subsequent updates (comments/fact-check)
                scroll.scrollTo(0, prevY);
            }
        });
        if (scroll != null) {
            scroll.requestFocus();
        } else {
            root.requestFocus();
        } // Ensure overlay keeps focus for D-pad navigation
    }

    public void setFooterMeta(CharSequence meta) {
        ensureInflated();
        if (footerMeta != null) {
            footerMeta.setText(meta != null ? meta : "");
            footerMeta.setVisibility(meta != null && meta.length() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    public void hide() {
        if (root != null) {
            // Stop TTS if speaking
            try { com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.instance(activity).stop(); } catch (Throwable ignored) {}
            root.setVisibility(View.GONE);
            if (previousFocus != null) previousFocus.requestFocus();
        }
    }

    public boolean isVisible() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }


    public CharSequence getCurrentText() {
        ensureInflated();
        return text != null ? text.getText() : "";
    }

    private void toggleSpeak() {
        com.liskovsoft.smartyoutubetv2.common.utils.TtsManager tts = com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.instance(activity);
        if (tts.isSpeaking()) {
            tts.stop();
            setListenButtonState(false);
        } else {
            CharSequence body = getCurrentText();
            if (body != null && body.length() > 0) {
                setListenButtonState(true);
                tts.addListener(ttsListener);
                tts.speak(body);
            }
        }
    }

    private final com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.Listener ttsListener = new com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.Listener() {
        @Override public void onStart() { setListenButtonState(true); }
        @Override public void onDone() { setListenButtonState(false); cleanupTtsListener(); }
        @Override public void onError(String error) { setListenButtonState(false); cleanupTtsListener(); }
        private void cleanupTtsListener() {
            try { com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.instance(activity).removeListener(this); } catch (Throwable ignored) {}
        }
    };

    private void setListenButtonState(boolean speaking) {
        if (listenBtn == null) return;
        try {
            listenBtn.setText(activity.getString(speaking ? com.liskovsoft.smartyoutubetv2.common.R.string.tts_stop : com.liskovsoft.smartyoutubetv2.common.R.string.tts_listen));
        } catch (Throwable ignored) {}
    }

    private static boolean isDescendant(ViewGroup parent, View child) {
        if (parent == null || child == null) return false;
        View current = child;
        while (current != null) {
            if (current == parent) return true;
            if (!(current.getParent() instanceof View)) return false;
            current = (View) current.getParent();
        }
        return false;
    }
}





