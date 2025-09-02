package com.liskovsoft.smartyoutubetv2.common.misc;

public interface AIClient {
    boolean isConfigured();
    String getLastUsedModel();

    String summarize(String title, String author, String videoId) throws java.io.IOException, org.json.JSONException;
    String summarize(String title, String author, String videoId, String detailLevel) throws java.io.IOException, org.json.JSONException;
    String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds) throws java.io.IOException, org.json.JSONException;
    String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds, String forceMode) throws java.io.IOException, org.json.JSONException;

    String summarizeComments(String videoTitle, String author, String videoId, java.util.List<String> comments, int analyzedCount) throws java.io.IOException, org.json.JSONException;

    String factCheck(String summary, String title, String author, String videoId);
}

