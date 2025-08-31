
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
    private static final String MODEL = "gemini-2.0-flash-exp"; // Optimized for lower latency
    private static final String FALLBACK_MODEL = "gemini-2.5-flash";
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=";
    private final String apiKey;
    private final String prefLang;
    private final boolean debug;
    private String lastUsedModel;

    public GeminiClient(Context context) {
        this.apiKey = loadApiKey(context);
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(context);
        this.prefLang = gd.getPreferredLanguage();
        this.debug = gd.isDebugLogging();
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(apiKey);
    }
    
    public String getLastUsedModel() {
        return lastUsedModel != null ? lastUsedModel : MODEL;
    }

    public String summarize(String title, String author, String videoId) throws IOException, JSONException {
        return summarize(title, author, videoId, "moderate");
    }
    
    public String summarize(String title, String author, String videoId, String detailLevel) throws IOException, JSONException {
        return summarize(title, author, videoId, detailLevel, 0);
    }

    public String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds) throws IOException, JSONException {
        return summarize(title, author, videoId, detailLevel, startTimeSeconds, null);
    }
    
    public String summarize(String title, String author, String videoId, String detailLevel, int startTimeSeconds, String forceMode) throws IOException, JSONException {
        android.util.Log.d("GeminiClient", "=== GEMINI SUMMARIZE DEBUG ===");
        android.util.Log.d("GeminiClient", "Title: " + title);
        android.util.Log.d("GeminiClient", "Author: " + author);
        android.util.Log.d("GeminiClient", "VideoID: " + videoId);
        android.util.Log.d("GeminiClient", "DetailLevel: " + detailLevel);
        android.util.Log.d("GeminiClient", "StartTime: " + startTimeSeconds);
        
        if (TextUtils.isEmpty(apiKey)) {
            return "Gemini API key not set. Put API_KEY in assets/gemini.properties";
        }

        // Mode selection: URL (Gemini watches video) vs Transcript/CC
        String mode = forceMode != null ? forceMode : com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData
                .instance(com.liskovsoft.youtubeapi.app.AppService.instance().getContext())
                .getMode();
        android.util.Log.d("GeminiClient", "Mode: " + mode + (forceMode != null ? " (forced)" : " (from settings)"));
        if (!TextUtils.isEmpty(mode) && "transcript".equalsIgnoreCase(mode)) {
            // Transcript/CC method (previous behavior)
            String transcript = null;
            String transcriptSource = null;
            try {
                TranscriptResult result = fetchTranscriptWithSource(videoId);
                transcript = result.transcript;
                transcriptSource = result.source;
            } catch (Exception e) {
                android.util.Log.w("GeminiClient", "Could not fetch transcript for " + videoId + ": " + e.getMessage());
            }

            boolean officialAvailable = hasOfficialTrack(videoId);
            String prompt = buildPrompt(title, author, videoId, detailLevel, transcript, transcriptSource, officialAvailable);
            try {
                String summary = callGemini(prompt);
                return appendFactCheckIfEnabled(summary, title, author, videoId);
            } catch (IOException e) {
                if (!TextUtils.isEmpty(transcript)) {
                    String[] chunks = splitTranscript(transcript, 12000);
                    StringBuilder partials = new StringBuilder();
                    for (int i = 0; i < chunks.length; i++) {
                        String chunkPrompt = buildPrompt(title + " (chunk " + (i+1) + "/" + chunks.length + ")", author, videoId, detailLevel, chunks[i], transcriptSource, officialAvailable);
                        String part = callGemini(chunkPrompt);
                        partials.append("\n[Chunk ").append(i+1).append("]\n").append(part).append("\n");
                    }
                    String combine = "Summarize the following partial summaries into a single cohesive summary with the same format and header requirements.\n\n" + partials;
                    String summary = callGemini(combine);
                    return appendFactCheckIfEnabled(summary, title, author, videoId);
                }
                throw e;
            }
        } else {
            // URL mode: ask Gemini 2.5 Flash to watch the YouTube URL directly
            android.util.Log.d("GeminiClient", "Using URL mode");
            String watchUrl = "";
            if (!TextUtils.isEmpty(videoId)) {
                StringBuilder url = new StringBuilder("https://www.youtube.com/watch?v=").append(videoId);
                if (startTimeSeconds > 0) url.append("&t=").append(startTimeSeconds).append('s');
                watchUrl = url.toString();
                android.util.Log.d("GeminiClient", "Constructed URL: " + watchUrl);
            } else {
                android.util.Log.w("GeminiClient", "VideoID is empty or null!");
                return "Error: No video ID provided";
            }
            String prompt;
            String lvl = detailLevel != null ? detailLevel.toLowerCase() : "moderate";
            switch (lvl) {
                case "concise":
                    prompt = "Provide a short summary of this video.";
                    break;
                case "detailed":
                    prompt = "Provide the most detailed summary of this video, including visual and auditory information.";
                    break;
                case "moderate":
                default:
                    prompt = "Provide a detailed summary of this video with key topics and timestamps.";
                    break;
            }
            // Use proper API structure: pass URL as fileData, not in text prompt
            android.util.Log.d("GeminiClient", "Sending URL as fileData to Gemini API");
            String summary = callGemini(prompt, watchUrl);
            return appendFactCheckIfEnabled(summary, title, author, videoId);
        }
    }

    private static String buildPrompt(String title, String author, String videoId, String detailLevel, String transcript, String transcriptSource, boolean officialAvailable) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant for Android TV. Summarize this YouTube video for TV reading.\n");
        sb.append("IMPORTANT: Start your response with a header line showing the detail level and sources used:\n");
        sb.append("Format: '[Detail Level: " + capitalize(detailLevel) + "] [Source: " + 
                  (transcriptSource != null ? transcriptSource : "Title/Metadata Only") + "] [Official CC Available: " + (officialAvailable ? "Yes" : "No") + "]'\n");
        sb.append("Then add a blank line before the actual summary.\n\n");
        
        // Add detail level specific instructions
        switch (detailLevel.toLowerCase()) {
            case "concise":
                sb.append("Keep it VERY brief - maximum 2-3 bullet points. Focus only on the main topic and key takeaway.\n");
                break;
            case "detailed":
                sb.append("Provide: a comprehensive summary with multiple bullet points.\n");
                sb.append("Include: detailed topic overview, main points covered, key takeaways, and any important conclusions.\n");
                break;
            case "moderate":
            default:
                sb.append("Use bullet points and short paragraphs.\n");
                sb.append("Include: topic, key takeaways, punchline (if it's a review or list or clickbait type video).\n");
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
        if (debug) android.util.Log.d("GeminiClient", "Attempting to fetch transcript for videoId: " + videoId);

        // Method 00: InnerTube get_transcript using watch page params
        try {
            String innerTubeTranscript = fetchTranscriptViaInnerTube(videoId);
            if (!TextUtils.isEmpty(innerTubeTranscript)) {
                android.util.Log.d("GeminiClient", "Transcript via get_transcript found, length: " + innerTubeTranscript.length());
                return new TranscriptResult(cleanTranscript(innerTubeTranscript), "InnerTube Transcript");
            }
        } catch (Exception e) {
            android.util.Log.d("GeminiClient", "InnerTube get_transcript failed for " + videoId + ": " + e.getMessage());
        }

        // Method 0: Use YouTube API (MediaServiceCore) captionTracks for robust URLs
        try {
            TranscriptResult apiResult = fetchCaptionsViaApi(videoId);
            if (apiResult != null && !TextUtils.isEmpty(apiResult.transcript)) {
                if (debug) android.util.Log.d("GeminiClient", "Transcript via API found (" + apiResult.source + "), length: " + apiResult.transcript.length());
                return apiResult;
            }
        } catch (Exception e) {
            android.util.Log.w("GeminiClient", "API captions failed for " + videoId + ": " + e.getMessage());
        }
        
        // Method 1: Try YouTube's timedtext API (official captions)
        try {
            if (debug) android.util.Log.d("GeminiClient", "Trying official captions for " + videoId);
            String transcript = fetchYouTubeTimedText(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                if (debug) android.util.Log.d("GeminiClient", "Official captions found, length: " + transcript.length());
                return new TranscriptResult(cleanTranscript(transcript), "Official Closed Captions");
            }
        } catch (Exception e) {
            android.util.Log.w("GeminiClient", "Official captions failed for " + videoId + ": " + e.getMessage());
        }
        
        // Method 2: Try auto-generated captions
        try {
            if (debug) android.util.Log.d("GeminiClient", "Trying auto-generated captions for " + videoId);
            String transcript = fetchAutoGeneratedCaptions(videoId);
            if (!TextUtils.isEmpty(transcript)) {
                if (debug) android.util.Log.d("GeminiClient", "Auto-generated captions found, length: " + transcript.length());
                return new TranscriptResult(cleanTranscript(transcript), "Auto-Generated Captions");
            }
        } catch (Exception e) {
            android.util.Log.w("GeminiClient", "Auto-generated captions failed for " + videoId + ": " + e.getMessage());
        }
        
        android.util.Log.w("GeminiClient", "No transcript found for " + videoId);
        return new TranscriptResult(null, null); // No transcript found
    }

    private String fetchTranscriptViaInnerTube(String videoId) throws IOException, JSONException {
        // Step 1: fetch watch page and extract getTranscriptEndpoint params
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId +
                "&hl=" + (TextUtils.isEmpty(prefLang) ? "en" : prefLang) + "&gl=US&bpctr=9999999999&has_verified=1";
        String page = makeHttpRequest(watchUrl);
        if (TextUtils.isEmpty(page)) return null;
        android.util.Log.d("GeminiClient", "Fetched watch page for transcript, length=" + page.length());
        // Regex for params string
        Pattern p = Pattern.compile("\\\"getTranscriptEndpoint\\\"\\s*:\\s*\\{[^}]*\\\"params\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher m = p.matcher(page);
        if (!m.find()) {
            android.util.Log.d("GeminiClient", "getTranscriptEndpoint params not found");
            return null;
        }
        String params = m.group(1);
        android.util.Log.d("GeminiClient", "Found transcript params");

        // Step 2: call youtubei/v1/get_transcript with context
        String apiKey = com.liskovsoft.youtubeapi.common.helpers.AppConstants.API_KEY;
        String url = "https://www.youtube.com/youtubei/v1/get_transcript?key=" + apiKey;
        JSONObject context = new JSONObject();
        JSONObject client = new JSONObject();
        client.put("clientName", com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB.getClientName());
        client.put("clientVersion", com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB.getClientVersion());
        JSONObject ctx = new JSONObject();
        ctx.put("client", client);
        context.put("context", ctx);
        context.put("params", params);

        okhttp3.OkHttpClient clientHttp = com.liskovsoft.googlecommon.common.helpers.RetrofitOkHttpHelper.getClient();
        okhttp3.MediaType mt = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mt, context.toString());
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-Goog-Visitor-Id", String.valueOf(com.liskovsoft.youtubeapi.app.AppService.instance().getVisitorData()))
                .build();
        try (okhttp3.Response resp = clientHttp.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            String json = resp.body() != null ? resp.body().string() : null;
            if (TextUtils.isEmpty(json)) return null;
            android.util.Log.d("GeminiClient", "get_transcript HTTP ok, len=" + json.length());
            String t = extractTranscriptFromGetTranscript(json);
            if (!TextUtils.isEmpty(t)) return t;
            // Retry with WEB_EMBED client if empty
            client.put("clientName", com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB_EMBED.getClientName());
            client.put("clientVersion", com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB_EMBED.getClientVersion());
            okhttp3.RequestBody body2 = okhttp3.RequestBody.create(mt, context.toString());
            okhttp3.Request req2 = new okhttp3.Request.Builder()
                    .url(url)
                    .post(body2)
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Visitor-Id", String.valueOf(com.liskovsoft.youtubeapi.app.AppService.instance().getVisitorData()))
                    .build();
            try (okhttp3.Response resp2 = clientHttp.newCall(req2).execute()) {
                if (!resp2.isSuccessful()) return null;
                String json2 = resp2.body() != null ? resp2.body().string() : null;
                android.util.Log.d("GeminiClient", "get_transcript HTTP ok (embed), len=" + (json2 != null ? json2.length() : 0));
                return extractTranscriptFromGetTranscript(json2);
            }
        }
    }

    private String extractTranscriptFromGetTranscript(String json) {
        try {
            Object root = new JSONObject(json);
            StringBuilder sb = new StringBuilder();
            collectTranscriptText(root, sb);
            String out = sb.toString().trim();
            return TextUtils.isEmpty(out) ? null : out;
        } catch (Throwable t) {
            return null;
        }
    }

    private void collectTranscriptText(Object node, StringBuilder out) {
        if (node == null) return;
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            // transcriptCueRenderer -> cue -> simpleText or runs[].text
            if (obj.has("transcriptCueRenderer")) {
                JSONObject cueR = obj.optJSONObject("transcriptCueRenderer");
                if (cueR != null) {
                    JSONObject cue = cueR.optJSONObject("cue");
                    if (cue != null) appendTextFromCue(cue, out);
                }
            }
            // transcriptSegmentRenderer -> snippet -> runs[].text
            if (obj.has("transcriptSegmentRenderer")) {
                JSONObject seg = obj.optJSONObject("transcriptSegmentRenderer");
                if (seg != null) {
                    JSONObject snip = seg.optJSONObject("snippet");
                    if (snip != null) appendTextFromCue(snip, out);
                }
            }
            // Recurse children
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.optString(i);
                    collectTranscriptText(obj.opt(k), out);
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) collectTranscriptText(arr.opt(i), out);
        }
    }

    private void appendTextFromCue(JSONObject cueOrSnippet, StringBuilder out) {
        if (cueOrSnippet == null) return;
        String t = cueOrSnippet.optString("simpleText", null);
        if (!TextUtils.isEmpty(t)) {
            out.append(t).append(' ');
            return;
        }
        JSONArray runs = cueOrSnippet.optJSONArray("runs");
        if (runs != null) {
