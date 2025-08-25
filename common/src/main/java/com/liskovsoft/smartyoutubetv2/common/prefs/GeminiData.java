package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

public class GeminiData {
    private static final String KEY_ENABLED = "gemini_enabled";
    private static final String KEY_DELAY_MS = "gemini_delay_ms";
    @SuppressLint("StaticFieldLeak")
    private static GeminiData sInstance;
    private final AppPrefs mPrefs;

    private GeminiData(Context ctx) {
        mPrefs = AppPrefs.instance(ctx);
    }

    public static GeminiData instance(Context ctx) {
        if (sInstance == null) sInstance = new GeminiData(ctx.getApplicationContext());
        return sInstance;
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_ENABLED, enabled);
    }

    public int getDelayMs() {
        return mPrefs.getInt(KEY_DELAY_MS, 5000);
    }

    public void setDelayMs(int ms) {
        mPrefs.putInt(KEY_DELAY_MS, ms);
    }
}

