package com.liskovsoft.smartyoutubetv2.common.misc;

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
import java.util.List;
import java.util.Properties;

/**
 * Minimal OpenAI client for summaries via Chat Completions API.
 * Reads API key from assets/openai.properties (API_KEY=...).
 * For video content, uses transcript/CC mode (URL mode is not supported for OpenAI).
 */
public class OpenAIClient implements AIClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final Context ctx;
    private final String apiKey;
    private String lastUsedModel;
    private Integer lastPromptTokens;
    private Integer lastCompletionTokens;
    private Integer lastTotalTokens;

    public OpenAIClient(Context context) {
        this.ctx = context.getApplicationContext();
        String k = null;
        try {
            k = com.liskovsoft.smartyoutubetv2.common.prefs.ApiKeyPrefs.instance(this.ctx).getOpenAIKey();
            if (k != null) k = k.trim();
        } catch (Throwable ignore) { }
        this.apiKey = !TextUtils.isEmpty(k) ? k : loadApiKey(this.ctx);
    }

    @Override
    public boolean isConfigured() {
        return !TextUtils.isEmpty(apiKey);
    }

    @Override
    public String getLastUsedModel() {
        return lastUsedModel != null ? lastUsedModel : getModel();
    }

    public Integer getLastPromptTokens() { return lastPromptTokens; }
    public Integer getLastCompletionTokens() { return lastCompletionTokens; }
    public Integer getLastTotalTokens() { return lastTotalTokens; }

    private String getModel() {
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(ctx);
        String custom = gd.getOpenAICustomModel();
        if (!TextUtils.isEmpty(custom)) return custom; // explicit override
        String m = gd.getOpenAIModel();
        if (TextUtils.isEmpty(m)) m = "gpt5-mini";
        return resolveModelId(m);
    }

    private static String resolveModelId(String configured) {
        if (configured == null) return "gpt-4o-mini";
        String c = configured.trim().toLowerCase();
        // Map friendly aliases to current OpenAI model IDs
        if ("gpt5-mini".equals(c)) return "gpt-5-mini";
        if ("gpt5".equals(c)) return "gpt-5";
        if ("gpt5-nano".equals(c)) return "gpt-5-nano";
        // If the user entered a direct model name, pass it through
        return configured;
    }

    @Override
    public String summarize(String title, String author, String videoId) throws IOException, JSONException {
        return summarize(title, author, videoId, "moderate");
    }

    @Override
    public String summarize(String title, String author, String videoId, String detailLevel) throws IOException, JSONException {
        return summarize(title, author, videoId, detailLevel, 0);
    }

    @Override
    public String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds) throws IOException, JSONException {
        return summarize(title, author, videoId, detailLevel, startTimeSeconds, null);
    }

    @Override
    public String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds, String forceMode) throws IOException, JSONException {
        android.util.Log.d("OpenAIClient", "=== OPENAI SUMMARIZE DEBUG ===");
        if (TextUtils.isEmpty(apiKey)) {
            return "OpenAI API key not set. Put API_KEY in assets/openai.properties";
        }

        // Always use transcript mode for OpenAI
        String transcript = GeminiClient.fetchTranscriptForSummary(ctx, videoId);
        boolean officialAvailable = GeminiClient.hasOfficialCC(ctx, videoId);
        String prompt = buildPrompt(title, author, videoId, detailLevel, transcript, officialAvailable);
        String result = callOpenAI(prompt);
        lastUsedModel = getModel();
        return result;
    }

    @Override
    public String summarizeComments(String videoTitle, String author, String videoId, List<String> comments, int analyzedCount) throws IOException, JSONException {
        if (comments == null || comments.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("You are summarizing viewer comments for an Android TV overlay.\n");
        sb.append("Provide a concise 'Comments Summary' with: common themes, consensus, notable insights, disagreements, sentiment, and useful viewer tips.\n");
        sb.append("Avoid quoting long texts; no personal data; be neutral.\n");
        sb.append("Keep it brief and skimmable (bullet points preferred).\n\n");
        if (!TextUtils.isEmpty(videoTitle)) sb.append("Video: ").append(videoTitle).append("\n");
        if (!TextUtils.isEmpty(author)) sb.append("Channel: ").append(author).append("\n");
        if (!TextUtils.isEmpty(videoId)) sb.append("VideoID: ").append(videoId).append("\n\n");
        sb.append("Sample of top comments (truncated):\n");
        int perLen = 220;
        for (String c : comments) {
            if (c == null) continue;
            String t = c.trim();
            if (t.isEmpty()) continue;
            if (t.length() > perLen) t = t.substring(0, perLen) + "…";
            sb.append("- ").append(t.replace('\n', ' ')).append("\n");
        }
        sb.append("\nNow produce the Comments Summary.\n");
        sb.append("End with a footer line: '--- Comments analyzed: ").append(analyzedCount).append("'.");
        String prompt = sb.toString();
        String result = callOpenAI(prompt);
        lastUsedModel = getModel();
        return result;
    }

    @Override
    public String factCheck(String summary, String title, String author, String videoId) {
        try {
            String prompt = "Fact-check the following video summary using your general knowledge base. " +
                    "Identify key claims, statistics, dates, and factual assertions. " +
                    "Return results as '**Fact Check Results:**' followed by bullet points indicating which claims were verified, corrections, and any uncertainties.\n\n" +
                    "Video: " + (title != null ? title : "") + (author != null ? " by " + author : "") + "\n\n" +
                    "Summary to fact-check:\n" + (summary != null ? summary : "");
            String result = callOpenAI(prompt);
            lastUsedModel = getModel();
            return result;
        } catch (Throwable t) {
            return "Fact Check: unavailable — " + (t.getMessage() != null ? t.getMessage() : "error");
        }
    }

    private static String buildPrompt(String title, String author, String videoId, String detailLevel, String transcript, boolean officialAvailable) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant for Android TV. Summarize this YouTube video for TV reading.\n");
        sb.append("IMPORTANT: End your response with technical details at the bottom:\n");
        sb.append("Format: '\\n---\\nDetail Level: ").append(capitalize(detailLevel)).append(" | Source: ")
                .append(!TextUtils.isEmpty(transcript) ? "Transcript" : "Title/Metadata Only")
                .append(" | Official CC Available: ").append(officialAvailable ? "Yes" : "No").append("'\n");
        sb.append("Start directly with the summary content (no header at the top).\n\n");

        String lvl = (detailLevel != null ? detailLevel.toLowerCase() : "moderate");
        switch (lvl) {
            case "concise":
                sb.append("Keep it VERY brief - maximum 2-3 bullet points.\n");
                break;
            case "detailed":
                sb.append("Provide a comprehensive summary: topic overview, main points, key takeaways, and conclusions.\n");
                break;
            default:
                sb.append("Use bullet points and short paragraphs. Include topic, key takeaways, and punchline if relevant.\n");
                break;
        }

        sb.append("\n");
        if (!TextUtils.isEmpty(title)) sb.append("Title: ").append(title).append("\n");
        if (!TextUtils.isEmpty(author)) sb.append("Channel: ").append(author).append("\n");
        if (!TextUtils.isEmpty(videoId)) sb.append("VideoID: ").append(videoId).append("\n");
        if (!TextUtils.isEmpty(transcript)) {
            sb.append("\nVideo Transcript:\n").append(transcript).append("\n");
        } else {
            sb.append("\nNote: No transcript available. Summarize based on title and channel.\n");
        }
        return sb.toString();
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String callOpenAI(String prompt) throws IOException, JSONException {
        String model = getModel();
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You write clear, concise, skimmable summaries for TV screens."));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(90000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("OpenAI HTTP " + code + ":\n" + resp);
        }

        JSONObject json = new JSONObject(resp);
        JSONArray choices = json.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                JSONObject msg = choice.optJSONObject("message");
                if (msg != null) {
                    // Usage parsing
                    try {
                        JSONObject usage = json.optJSONObject("usage");
                        if (usage != null) {
                            lastPromptTokens = usage.has("prompt_tokens") ? usage.optInt("prompt_tokens") : null;
                            lastCompletionTokens = usage.has("completion_tokens") ? usage.optInt("completion_tokens") : null;
                            lastTotalTokens = usage.has("total_tokens") ? usage.optInt("total_tokens") : null;
                        } else {
                            lastPromptTokens = lastCompletionTokens = lastTotalTokens = null;
                        }
                    } catch (Throwable ignore) {
                        lastPromptTokens = lastCompletionTokens = lastTotalTokens = null;
                    }
                    return msg.optString("content", resp);
                }
            }
        }
        // Reset usage if not found
        lastPromptTokens = lastCompletionTokens = lastTotalTokens = null;
        return resp;
    }

    private static String loadApiKey(Context ctx) {
        try {
            AssetManager am = ctx.getAssets();
            try (InputStream is = am.open("openai.properties")) {
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
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
