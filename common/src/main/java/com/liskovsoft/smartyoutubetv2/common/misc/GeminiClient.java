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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Gemini client using REST API. Reads API key from assets/gemini.properties (API_KEY=...).
 */
public class GeminiClient {
    
    private static class TranscriptResult {
        String transcript;
        String source;
        
        TranscriptResult(String transcript, String source) {
            this.transcript = transcript;
            this.source = source;
        }
    }
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";
    private final String apiKey;

    public GeminiClient(Context context) {
        this.apiKey = loadApiKey(context);
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(apiKey);
    }

    public String summarize(String title, String author, String videoId) throws IOException, JSONException {
        return summarize(title, author, videoId, "moderate");
    }
    
    public String summarize(String title, String author, String videoId, String detailLevel) throws IOException, JSONException {
        if (TextUtils.isEmpty(apiKey)) {
            return "Gemini API key not set. Put API_KEY in assets/gemini.properties";
        }
        
        // Try to fetch transcript for more accurate summaries
        String transcript = null;
        String transcriptSource = null;
        try {
            TranscriptResult result = fetchTranscriptWithSource(videoId);
            transcript = result.transcript;
            transcriptSource = result.source;
        } catch (Exception e) {
            // Transcript fetch failed, continue with title-only summary
            android.util.Log.w("GeminiClient", "Could not fetch transcript for " + videoId + ": " + e.getMessage());
        }
        
        String prompt = buildPrompt(title, author, videoId, detailLevel, transcript, transcriptSource);
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

    private static String buildPrompt(String title, String author, String videoId, String detailLevel, String transcript, String transcriptSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant for Android TV. Summarize this YouTube video for TV reading.\n");
        sb.append("IMPORTANT: Start your response with a header line showing the detail level and sources used:\n");
        sb.append("Format: '[Detail Level: " + capitalize(detailLevel) + "] [Source: " + 
                  (transcriptSource != null ? transcriptSource : "Title/Metadata Only") + "]'\n");
        sb.append("Then add a blank line before the actual summary.\n\n");
        
        // Add detail level specific instructions
        switch (detailLevel.toLowerCase()) {
            case "concise":
                sb.append("Keep it VERY brief - maximum 2-3 bullet points. Focus only on the main topic and key takeaway.\n");
                break;
            case "detailed":
                sb.append("Provide a comprehensive summary with multiple bullet points.\n");
                sb.append("Include: detailed topic overview, key takeaways, target audience, main points covered, and any important conclusions.\n");
                break;
            case "moderate":
            default:
                sb.append("Use bullet points and short paragraphs.\n");
                sb.append("Include: topic, key takeaways, who it's for.\n");
                break;
        }
        
        sb.append("\n");
        if (!TextUtils.isEmpty(title)) sb.append("Title: ").append(title).append("\n");
        if (!TextUtils.isEmpty(author)) sb.append("Channel: ").append(author).append("\n");
        if (!TextUtils.isEmpty(videoId)) sb.append("VideoID: ").append(videoId).append("\n");
        
        // Include transcript if available
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

    private String fetchTranscript(String videoId) throws IOException, JSONException {
        // Try multiple methods to get transcript
        String transcript = null;
        
        // Method 1: Try YouTube's timedtext API (official captions)
        try {
            transcript = fetchYouTubeTimedText(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                return cleanTranscript(transcript);
            }
        } catch (Exception e) {
            android.util.Log.d("GeminiClient", "Official captions failed for " + videoId + ": " + e.getMessage());
        }
        
        // Method 2: Try auto-generated captions
        try {
            transcript = fetchAutoGeneratedCaptions(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                return cleanTranscript(transcript);
            }
        } catch (Exception e) {
            android.util.Log.d("GeminiClient", "Auto-generated captions failed for " + videoId + ": " + e.getMessage());
        }
        
        return null; // No transcript found
    }
    
    private TranscriptResult fetchTranscriptWithSource(String videoId) throws IOException, JSONException {
        android.util.Log.d("GeminiClient", "Attempting to fetch transcript for videoId: " + videoId);
        
        // Method 1: Try YouTube's timedtext API (official captions)
        try {
            android.util.Log.d("GeminiClient", "Trying official captions for " + videoId);
            String transcript = fetchYouTubeTimedText(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                android.util.Log.d("GeminiClient", "Official captions found, length: " + transcript.length());
                return new TranscriptResult(cleanTranscript(transcript), "Official Closed Captions");
            }
        } catch (Exception e) {
            android.util.Log.d("GeminiClient", "Official captions failed for " + videoId + ": " + e.getMessage());
        }
        
        // Method 2: Try auto-generated captions
        try {
            android.util.Log.d("GeminiClient", "Trying auto-generated captions for " + videoId);
            String transcript = fetchAutoGeneratedCaptions(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                android.util.Log.d("GeminiClient", "Auto-generated captions found, length: " + transcript.length());
                return new TranscriptResult(cleanTranscript(transcript), "Auto-Generated Captions");
            }
        } catch (Exception e) {
            android.util.Log.d("GeminiClient", "Auto-generated captions failed for " + videoId + ": " + e.getMessage());
        }
        
        android.util.Log.d("GeminiClient", "No transcript found for " + videoId);
        return new TranscriptResult(null, null); // No transcript found
    }
    
    private String fetchYouTubeTimedText(String videoId) throws IOException {
        // Try common language codes
        String[] langCodes = {"en", "en-US", "en-GB"};
        
        for (String lang : langCodes) {
            try {
                String url = "https://www.youtube.com/api/timedtext?v=" + videoId + "&lang=" + lang;
                String response = makeHttpRequest(url);
                if (!TextUtils.isEmpty(response) && !response.contains("error")) {
                    return extractTextFromTimedText(response);
                }
            } catch (Exception e) {
                // Try next language
            }
        }
        
        throw new IOException("No timed text available");
    }
    
    private String fetchAutoGeneratedCaptions(String videoId) throws IOException, JSONException {
        // Get video page to find auto-generated caption tracks
        String videoPageUrl = "https://www.youtube.com/watch?v=" + videoId;
        String videoPage = makeHttpRequest(videoPageUrl);
        
        // Look for caption tracks in the video page
        Pattern captionPattern = Pattern.compile("\"captionTracks\":\\[([^\\]]+)\\]");
        Matcher matcher = captionPattern.matcher(videoPage);
        
        if (matcher.find()) {
            String captionsData = matcher.group(1);
            // Extract auto-generated caption URL
            Pattern urlPattern = Pattern.compile("\"baseUrl\":\"([^\"]+)\"");
            Matcher urlMatcher = urlPattern.matcher(captionsData);
            
            if (urlMatcher.find()) {
                String captionUrl = urlMatcher.group(1).replace("\\u0026", "&");
                String response = makeHttpRequest(captionUrl);
                return extractTextFromTimedText(response);
            }
        }
        
        throw new IOException("No auto-generated captions available");
    }
    
    private String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        } finally {
            conn.disconnect();
        }
    }
    
    private String extractTextFromTimedText(String timedTextXml) {
        // Extract text content from YouTube's timed text XML format
        StringBuilder text = new StringBuilder();
        Pattern textPattern = Pattern.compile("<text[^>]*>([^<]+)</text>");
        Matcher matcher = textPattern.matcher(timedTextXml);
        
        while (matcher.find()) {
            String textContent = matcher.group(1);
            // Decode HTML entities
            textContent = textContent.replace("&amp;", "&")
                                   .replace("&lt;", "<")
                                   .replace("&gt;", ">")
                                   .replace("&quot;", "\"")
                                   .replace("&#39;", "'");
            text.append(textContent).append(" ");
        }
        
        return text.toString().trim();
    }
    
    private String cleanTranscript(String transcript) {
        if (TextUtils.isEmpty(transcript)) return transcript;
        
        // Clean up the transcript for better processing
        transcript = transcript.replaceAll("\\s+", " "); // Normalize whitespace
        transcript = transcript.replaceAll("\\[.*?\\]", ""); // Remove [Music], [Applause], etc.
        transcript = transcript.replaceAll("\\(.*?\\)", ""); // Remove (background noise), etc.
        
        // Limit transcript length for API efficiency (keep first ~4000 chars for context)
        if (transcript.length() > 4000) {
            transcript = transcript.substring(0, 4000) + "... [transcript truncated]";
        }
        
        return transcript.trim();
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

