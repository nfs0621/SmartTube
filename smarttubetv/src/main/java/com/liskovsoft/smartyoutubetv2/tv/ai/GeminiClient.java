package com.liskovsoft.smartyoutubetv2.tv.ai;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Minimal Gemini client using REST API. Reads API key from assets/gemini.properties (API_KEY=...).
 */
public class GeminiClient {
    private static final String MODEL = "gemini-1.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";
    private final String apiKey;

    public GeminiClient(Context context) {
        this.apiKey = loadApiKey(context);
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(apiKey);
    }

    public String summarize(String title, String author, String videoId) throws IOException, JSONException {
        if (TextUtils.isEmpty(apiKey)) {
            return "Gemini API key not set. Put API_KEY in assets/gemini.properties";
        }
        String prompt = buildPrompt(title, author, videoId);
        JSONObject req = new JSONObject();
        JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
        JSONObject content = new JSONObject().put("parts", parts);
        req.put("contents", new JSONArray().put(content));

        byte[] payload = req.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + apiKey).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            return "Gemini error: " + code + "\n" + resp;
        }
        JSONObject json = new JSONObject(resp);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject contentResp = cand.optJSONObject("content");
            if (contentResp != null) {
                JSONArray partsResp = contentResp.optJSONArray("parts");
                if (partsResp != null && partsResp.length() > 0) {
                    return partsResp.getJSONObject(0).optString("text", resp);
                }
            }
        }
        return resp;
    }

    private static String buildPrompt(String title, String author, String videoId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant for Android TV. Summarize this YouTube video succinctly for TV reading.\n");
        sb.append("Use bullet points and short paragraphs.\n");
        sb.append("Include: topic, key takeaways, who it's for.\n\n");
        if (!TextUtils.isEmpty(title)) sb.append("Title: ").append(title).append("\n");
        if (!TextUtils.isEmpty(author)) sb.append("Channel: ").append(author).append("\n");
        if (!TextUtils.isEmpty(videoId)) sb.append("VideoID: ").append(videoId).append("\n");
        return sb.toString();
    }

    private static String loadApiKey(Context ctx) {
        try {
            AssetManager am = ctx.getAssets();
            try (InputStream is = am.open("gemini.properties")) {
                Properties p = new Properties();
                p.load(is);
                String k = p.getProperty("API_KEY", "").trim();
                return TextUtils.isEmpty(k) ? null : k;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}

