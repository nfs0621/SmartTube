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
    private Button listenBtn;
    private boolean firstContentShown;

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
        if (scroll != null) {
            scroll.setFocusable(true);
            scroll.setFocusableInTouchMode(true);
        }
        emailBtn = root.findViewById(R.id.gemini_email_btn);
        listenBtn = root.findViewById(R.id.gemini_listen_btn);

        if (emailBtn != null) {
            emailBtn.setFocusable(true);
            emailBtn.setFocusableInTouchMode(true);
            emailBtn.setOnClickListener(v -> {
                if (onEmailListener != null) onEmailListener.onEmail();
            });
        }
        if (listenBtn != null) {
            listenBtn.setFocusable(true);
            listenBtn.setFocusableInTouchMode(true);
            listenBtn.setOnClickListener(v -> toggleSpeak());
        }

        // Replace with a custom root that intercepts keys at the frame level
        android.widget.FrameLayout customRoot = new android.widget.FrameLayout(activity) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    View currentFocus = findFocus();
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                        case KeyEvent.KEYCODE_ESCAPE:
                            hide();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            toggleSpeak();
                            return true;
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            // Let focused child handle
                            break;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            if (currentFocus == emailBtn || currentFocus == listenBtn) {
                                if (scroll != null) scroll.requestFocus();
                                return true;
                            }
                            VideoSummaryOverlay.this.scrollBy(-200);
                            return true;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (scroll != null && scroll.canScrollVertically(1)) {
                                VideoSummaryOverlay.this.scrollBy(200);
                                return true;
                            }
                            // Toggle focus from Push -> Listen when pressing DOWN repeatedly
                            if (currentFocus == emailBtn && listenBtn != null) {
                                listenBtn.requestFocus();
                                return true;
                            }
                            if (currentFocus == listenBtn) {
                                return true;
                            }
                            // Coming from text or elsewhere: focus Push first, then Listen on next DOWN
                            if (emailBtn != null) {
                                emailBtn.requestFocus();
                                return true;
                            } else if (listenBtn != null) {
                                listenBtn.requestFocus();
                                return true;
                            }
                            return false;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            // Allow normal horizontal navigation between buttons
                            break;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };

        // Transfer params and children into custom root
        customRoot.setLayoutParams(root.getLayoutParams());
        try { customRoot.setBackground(root.getBackground()); } catch (Throwable ignored) {}
        customRoot.setClickable(true);
        customRoot.setFocusable(true);
        customRoot.setFocusableInTouchMode(true);
        ((ViewGroup) customRoot).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        ViewGroup currentGroup = (ViewGroup) root;
        while (currentGroup.getChildCount() > 0) {
            View child = currentGroup.getChildAt(0);
            currentGroup.removeView(child);
            customRoot.addView(child);
        }
        root = customRoot;

        content.addView(root);

        // Keep focus on overlay while visible (avoid leaking focus to underlying views)
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (isVisible()) {
                if (newFocus == null) {
                    root.requestFocus();
                }
            }
        });
    }

    private void scrollBy(int dy) {
        scroll.smoothScrollBy(0, dy);
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
                scroll.scrollTo(0, 0);
                firstContentShown = true;
                try {
                    if (com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(activity).isTtsSpeakOnOpen()) {
                        handler.postDelayed(this::toggleSpeak, 350);
                    }
                } catch (Throwable ignored) {}
            } else if (prevY > 0) {
                scroll.scrollTo(0, prevY);
            }
            if (scroll != null) {
                scroll.requestFocus();
            } else {
                root.requestFocus();
            }
        });
    }

    public void hide() {
        if (root != null) {
            try { com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.instance(activity).stop(); } catch (Throwable ignored) {}
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
        @Override public void onDone() { setListenButtonState(false); cleanup(); }
        @Override public void onError(String error) { setListenButtonState(false); cleanup(); }
        private void cleanup() {
            try { com.liskovsoft.smartyoutubetv2.common.utils.TtsManager.instance(activity).removeListener(this); } catch (Throwable ignored) {}
        }
    };

    private void setListenButtonState(boolean speaking) {
        if (listenBtn == null) return;
        listenBtn.setText(speaking ? activity.getString(R.string.tts_stop) : activity.getString(R.string.tts_listen));
    }
}




