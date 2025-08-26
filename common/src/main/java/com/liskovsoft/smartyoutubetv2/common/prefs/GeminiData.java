package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

public class GeminiData {
    private static final String KEY_ENABLED = "gemini_enabled";
    private static final String KEY_DELAY_MS = "gemini_delay_ms";
    private static final String KEY_DETAIL_LEVEL = "gemini_detail_level";
    private static final String KEY_LANG = "gemini_lang";
    private static final String KEY_DEBUG = "gemini_debug";
    private static final String KEY_MAX_CHARS = "gemini_max_chars";
    private static final String KEY_MODE = "gemini_mode"; // "url" or "transcript"
    private static final String KEY_MODEL = "gemini_model"; // model selection
    private static final String KEY_FACT_CHECK = "gemini_fact_check"; // fact check enabled
    private static final String KEY_MARK_WATCHED = "gemini_mark_watched"; // mark as watched on summary
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

    public String getDetailLevel() {
        return mPrefs.getString(KEY_DETAIL_LEVEL, "moderate");
    }

    public void setDetailLevel(String level) {
        mPrefs.putString(KEY_DETAIL_LEVEL, level);
    }

    public String getPreferredLanguage() {
        return mPrefs.getString(KEY_LANG, "en");
    }

    public void setPreferredLanguage(String lang) {
        mPrefs.putString(KEY_LANG, lang);
    }

    public boolean isDebugLogging() {
        return mPrefs.getBoolean(KEY_DEBUG, false);
    }

    public void setDebugLogging(boolean enabled) {
        mPrefs.putBoolean(KEY_DEBUG, enabled);
    }

    /**
     * 0 or negative means unlimited
     */
    public int getMaxTranscriptChars() {
        return mPrefs.getInt(KEY_MAX_CHARS, 0);
    }

    public void setMaxTranscriptChars(int max) {
        mPrefs.putInt(KEY_MAX_CHARS, max);
    }

    /**
     * Summary source mode: "url" (Gemini watches the video via URL) or "transcript" (use CC/transcript).
     */
    public String getMode() {
        return mPrefs.getString(KEY_MODE, "url");
    }

    public void setMode(String mode) {
        mPrefs.putString(KEY_MODE, mode);
    }

    /**
     * Gemini AI model selection.
     * Options: "auto", "gemini-2.0-flash-exp", "gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-1.0-pro"
     */
    public String getModel() {
        return mPrefs.getString(KEY_MODEL, "auto");
    }

    public void setModel(String model) {
        mPrefs.putString(KEY_MODEL, model);
    }

    public boolean isFactCheckEnabled() {
        return mPrefs.getBoolean(KEY_FACT_CHECK, true);
    }

    public void setFactCheckEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_FACT_CHECK, enabled);
    }

    public boolean isMarkAsWatchedEnabled() {
        return mPrefs.getBoolean(KEY_MARK_WATCHED, true);
    }

    public void setMarkAsWatchedEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_MARK_WATCHED, enabled);
    }
}

