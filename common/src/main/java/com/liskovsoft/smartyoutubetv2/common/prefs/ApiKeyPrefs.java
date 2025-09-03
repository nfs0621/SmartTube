package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

/**
 * Simple storage for a user-supplied API key.
 * Uses existing AppPrefs backend to avoid introducing new storage primitives.
 */
public class ApiKeyPrefs {
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";

    @SuppressLint("StaticFieldLeak")
    private static ApiKeyPrefs sInstance;
    private final AppPrefs mPrefs;

    private ApiKeyPrefs(Context ctx) {
        mPrefs = AppPrefs.instance(ctx.getApplicationContext());
    }

    public static ApiKeyPrefs instance(Context ctx) {
        if (sInstance == null) sInstance = new ApiKeyPrefs(ctx);
        return sInstance;
    }

    public void setOpenAIKey(String value) { mPrefs.putString(KEY_OPENAI_API_KEY, value); }
    public void setGeminiKey(String value) { mPrefs.putString(KEY_GEMINI_API_KEY, value); }
    public String getOpenAIKey() { return mPrefs.getString(KEY_OPENAI_API_KEY, null); }
    public String getGeminiKey() { return mPrefs.getString(KEY_GEMINI_API_KEY, null); }
    public void setApiKey(String provider, String value) {
        if (provider == null || value == null) return;
        if ("gemini".equalsIgnoreCase(provider)) setGeminiKey(value); else setOpenAIKey(value);
    }
    public void clearOpenAIKey() { mPrefs.putString(KEY_OPENAI_API_KEY, null); }
    public void clearGeminiKey() { mPrefs.putString(KEY_GEMINI_API_KEY, null); }
}
