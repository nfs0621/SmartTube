package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

public class GeminiData {
    // Removed: auto-summary enable and delay keys
    private static final String KEY_DETAIL_LEVEL = "gemini_detail_level";
    private static final String KEY_LANG = "gemini_lang";
    private static final String KEY_DEBUG = "gemini_debug";
    private static final String KEY_MAX_CHARS = "gemini_max_chars";
    private static final String KEY_MODE = "gemini_mode"; // "url" or "transcript"
    private static final String KEY_MODEL = "gemini_model"; // model selection
    private static final String KEY_FACT_CHECK = "gemini_fact_check"; // fact check enabled
    private static final String KEY_MARK_WATCHED = "gemini_mark_watched"; // mark as watched on summary
    private static final String KEY_SUMMARY_EMAIL = "gemini_summary_email"; // recipient email for summaries
    private static final String KEY_EMAIL_SUMMARIES_ENABLED = "gemini_email_summaries_enabled"; // email summaries feature enabled
    // Comments summary
    private static final String KEY_COMMENTS_SUMMARY_ENABLED = "gemini_comments_summary_enabled";
    private static final String KEY_COMMENTS_MAX = "gemini_comments_max";
    private static final String KEY_COMMENTS_SOURCE = "gemini_comments_source"; // "top" (default), reserved for future
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

    // Auto-summary enable and delay removed

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
        // Default to a tools-capable model for better compatibility (fact-check, etc.)
        return mPrefs.getString(KEY_MODEL, "gemini-2.5-flash");
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

    public String getSummaryEmail() {
        return mPrefs.getString(KEY_SUMMARY_EMAIL, null);
    }

    public void setSummaryEmail(String email) {
        mPrefs.putString(KEY_SUMMARY_EMAIL, email);
    }

    public boolean isEmailSummariesEnabled() {
        return mPrefs.getBoolean(KEY_EMAIL_SUMMARIES_ENABLED, false); // Default: off
    }

    public void setEmailSummariesEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_EMAIL_SUMMARIES_ENABLED, enabled);
    }

    // Comments summary prefs
    public boolean isCommentsSummaryEnabled() {
        return mPrefs.getBoolean(KEY_COMMENTS_SUMMARY_ENABLED, false);
    }

    public void setCommentsSummaryEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_COMMENTS_SUMMARY_ENABLED, enabled);
    }

    public int getCommentsMaxCount() {
        // Default to 50 as requested
        return mPrefs.getInt(KEY_COMMENTS_MAX, 50);
    }

    public void setCommentsMaxCount(int count) {
        mPrefs.putInt(KEY_COMMENTS_MAX, count);
    }

    public String getCommentsSource() {
        return mPrefs.getString(KEY_COMMENTS_SOURCE, "top");
    }

    public void setCommentsSource(String source) {
        mPrefs.putString(KEY_COMMENTS_SOURCE, source);
    }
}

