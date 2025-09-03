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
public class GeminiClient implements AIClient {
    private String lastFactCheckError;
    
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
    private Integer lastPromptTokens;
    private Integer lastCandidatesTokens;
    private Integer lastTotalTokens;

    public GeminiClient(Context context) {
        String k = null;
        try {
            k = com.liskovsoft.smartyoutubetv2.common.prefs.ApiKeyPrefs.instance(context).getGeminiKey();
            if (k != null) k = k.trim();
        } catch (Throwable ignore) { }
        this.apiKey = !TextUtils.isEmpty(k) ? k : loadApiKey(context);
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

    public Integer getLastPromptTokens() { return lastPromptTokens; }
    public Integer getLastCompletionTokens() { return lastCandidatesTokens; }
    public Integer getLastTotalTokens() { return lastTotalTokens; }

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
                return summary;
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
                    return summary;
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
            return summary;
        }
    }

    /**
     * Perform fact checking on an existing summary as a separate operation.
     * This allows for async fact checking independent of summary generation.
     */
    public String factCheck(String summary, String title, String author, String videoId) {
        try {
            com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = 
                    com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(
                            com.liskovsoft.youtubeapi.app.AppService.instance().getContext());
            
            if (!gd.isFactCheckEnabled()) {
                android.util.Log.d("GeminiClient", "Fact checking disabled");
                return null;
            }

            android.util.Log.d("GeminiClient", "✓ PERFORMING standalone fact check for: " + title);
            android.util.Log.d("GeminiClient", "Summary length: " + (summary != null ? summary.length() : "NULL") + " chars");
            // Clear previous error
            lastFactCheckError = null;
            String factCheck = performFactCheck(summary, title, author, videoId);
            android.util.Log.d("GeminiClient", "Fact check returned: " + (factCheck != null ? factCheck.length() + " chars" : "NULL"));
            
            if (!TextUtils.isEmpty(factCheck)) {
                return factCheck;
            }
            
            // Return error info if fact check failed
            String model = gd.getModel();
            String reason = !TextUtils.isEmpty(lastFactCheckError)
                    ? lastFactCheckError
                    : "empty response or tools not supported by selected model";
            return "Fact Check: unavailable (model: " + model + ") — " + reason;
        } catch (Exception e) {
            android.util.Log.w("GeminiClient", "Fact check failed: " + e.getMessage());
            String model = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData
                    .instance(com.liskovsoft.youtubeapi.app.AppService.instance().getContext())
                    .getModel();
            String reason = e.getMessage() != null ? e.getMessage() : "unexpected error";
            return "Fact Check: unavailable (model: " + model + ") — " + reason;
        }
    }

    private static String buildPrompt(String title, String author, String videoId, String detailLevel, String transcript, String transcriptSource, boolean officialAvailable) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an assistant for Android TV. Summarize this YouTube video for TV reading.\n");
        sb.append("IMPORTANT: End your response with technical details at the bottom:\n");
        sb.append("Format: '\\n---\\nDetail Level: " + capitalize(detailLevel) + " | Source: " + 
                  (transcriptSource != null ? transcriptSource : "Title/Metadata Only") + " | Official CC Available: " + (officialAvailable ? "Yes" : "No") + "'\n");
        sb.append("Start directly with the summary content (no header at the top).\n\n");
        
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
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i);
                if (run != null) {
                    String txt = run.optString("text", null);
                    if (!TextUtils.isEmpty(txt)) out.append(txt);
                }
            }
            out.append(' ');
        }
    }

    private TranscriptResult fetchCaptionsViaApi(String videoId) throws IOException {
        try {
            com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApi api =
                    com.liskovsoft.googlecommon.common.helpers.RetrofitHelper.create(
                            com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApi.class);
            com.liskovsoft.youtubeapi.common.helpers.AppClient[] clients = new com.liskovsoft.youtubeapi.common.helpers.AppClient[] {
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB_EMBED,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.MWEB,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.IOS,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.TV,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.TV_EMBED
            };
            com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo info = null;
            for (com.liskovsoft.youtubeapi.common.helpers.AppClient client : clients) {
                String q = com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApiHelper.getVideoInfoQuery(client, videoId, null);
                if (debug) android.util.Log.d("GeminiClient", "Trying player API with client=" + client.name());
                try {
                    // Use generic method; OkHttp helper will inject API key and headers
                    info = com.liskovsoft.googlecommon.common.helpers.RetrofitHelper.get(api.getVideoInfo(q));
                    if (info != null && info.getCaptionTracks() != null && !info.getCaptionTracks().isEmpty()) break;
                } catch (Throwable ignored) {
                }
            }
            if (info == null || info.getCaptionTracks() == null || info.getCaptionTracks().isEmpty()) {
                if (debug) android.util.Log.d("GeminiClient", "Player API returned no captionTracks");
                return null;
            }

            java.util.List<com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack> tracks = info.getCaptionTracks();
            if (debug) android.util.Log.d("GeminiClient", "Player API captions available: " + tracks.size());
            // Prefer official English, then autogenerated English, else first track
            com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack preferred = null;
            for (com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack t : tracks) {
                if ("en".equalsIgnoreCase(t.getLanguageCode()) && !t.isAutogenerated()) { preferred = t; break; }
            }
            if (preferred == null) {
                for (com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack t : tracks) {
                    if ("en".equalsIgnoreCase(t.getLanguageCode())) { preferred = t; break; }
                }
            }
            if (preferred == null) preferred = tracks.get(0);

            // Request VTT for simpler parsing
            com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack.sFormat = com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack.CaptionFormat.VTT;
            String url = preferred.getBaseUrl();
            // If track is translatable and not English, request English translation
            if (preferred.isTranslatable() && (preferred.getLanguageCode() == null || !"en".equalsIgnoreCase(preferred.getLanguageCode()))) {
                url += (url.contains("?") ? "&" : "?") + "tlang=en";
            }
            if (debug) android.util.Log.d("GeminiClient", "Fetching caption url: " + url);
            if (TextUtils.isEmpty(url)) return null;
            String resp = makeHttpRequest(url);
            String parsed = parseCaptionResponse(resp);
            if (TextUtils.isEmpty(parsed)) {
                if (debug) android.util.Log.d("GeminiClient", "Empty parse from caption url");
                return null;
            }
            String source = preferred.isAutogenerated() ? "Auto-Generated Captions" : "Official Closed Captions";
            return new TranscriptResult(cleanTranscript(parsed), source);
        } catch (Throwable t) {
            throw new IOException(t);
        }
    }
    
    private String fetchYouTubeTimedText(String videoId) throws IOException {
        // Try common language codes
        String[] langCodes = {"en", "en-US", "en-GB"};
        
        for (String lang : langCodes) {
            // Try XML first
            String base = "https://www.youtube.com/api/timedtext?v=" + videoId + "&lang=" + lang;
            String[] candidates = new String[] { base, base + "&fmt=vtt", base + "&fmt=json3" };
            for (String url : candidates) {
                try {
                    String response = makeHttpRequest(url);
                    if (TextUtils.isEmpty(response) || response.contains("error")) continue;
                    String parsed = parseCaptionResponse(response);
                    if (!TextUtils.isEmpty(parsed)) {
                        android.util.Log.d("GeminiClient", "Parsed timedtext using URL format: " + url);
                        return parsed;
                    }
                } catch (Exception e) {
                    // Try next format
                }
            }
        }
        
        throw new IOException("No timed text available");
    }
    
    private String fetchAutoGeneratedCaptions(String videoId) throws IOException, JSONException {
        // Get video page to find auto-generated caption tracks
        String videoPageUrl = "https://www.youtube.com/watch?v=" + videoId + "&hl=en&gl=US&bpctr=9999999999&has_verified=1";
        String videoPage = makeHttpRequest(videoPageUrl);
        android.util.Log.d("GeminiClient", "Fetched watch page, length=" + (videoPage != null ? videoPage.length() : 0));
        
        // Look for caption tracks in the video page
        Pattern captionPattern = Pattern.compile("\"captionTracks\":\\[([^\\]]+)\\]", Pattern.DOTALL);
        Matcher matcher = captionPattern.matcher(videoPage);
        
        if (matcher.find()) {
            android.util.Log.d("GeminiClient", "captionTracks found in watch page");
            String captionsData = matcher.group(1);
            // Extract auto-generated caption URL
            Pattern urlPattern = Pattern.compile("\"baseUrl\":\"([^\"]+)\"");
            Matcher urlMatcher = urlPattern.matcher(captionsData);
            
            if (urlMatcher.find()) {
                String captionUrl = urlMatcher.group(1).replace("\\u0026", "&");
                // Try multiple formats as baseUrl may omit fmt
                String[] variants = new String[] {
                    captionUrl,
                    captionUrl + (captionUrl.contains("?") ? "&" : "?") + "fmt=vtt",
                    captionUrl + (captionUrl.contains("?") ? "&" : "?") + "fmt=json3"
                };
                for (String url : variants) {
                    try {
                        String response = makeHttpRequest(url);
                        String parsed = parseCaptionResponse(response);
                        if (!TextUtils.isEmpty(parsed)) {
                            android.util.Log.d("GeminiClient", "Parsed auto captions using URL format: " + url);
                            return parsed;
                        }
                    } catch (Exception e) {
                        // try next variant
                    }
                }
            }
        } else {
            android.util.Log.d("GeminiClient", "captionTracks NOT found in watch page");
        }
        
        throw new IOException("No auto-generated captions available");
    }

    private boolean hasOfficialTrack(String videoId) {
        try {
            com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApi api =
                    com.liskovsoft.googlecommon.common.helpers.RetrofitHelper.create(
                            com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApi.class);
            com.liskovsoft.youtubeapi.common.helpers.AppClient[] clients = new com.liskovsoft.youtubeapi.common.helpers.AppClient[] {
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB_EMBED,
                    com.liskovsoft.youtubeapi.common.helpers.AppClient.WEB
            };
            for (com.liskovsoft.youtubeapi.common.helpers.AppClient client : clients) {
                String q = com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoApiHelper.getVideoInfoQuery(client, videoId, null);
                com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo info = com.liskovsoft.googlecommon.common.helpers.RetrofitHelper.get(api.getVideoInfo(q));
                if (info != null && info.getCaptionTracks() != null) {
                    for (com.liskovsoft.youtubeapi.videoinfo.models.CaptionTrack t : info.getCaptionTracks()) {
                        if (!t.isAutogenerated()) return true;
                    }
                    return false;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
    
    private String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        String langHdr = TextUtils.isEmpty(prefLang) ? "en-US,en;q=0.9" : (prefLang + ";q=1.0,en;q=0.8");
        conn.setRequestProperty("Accept-Language", langHdr);
        // Bypass EU consent page and enforce English content
        String cookie = "CONSENT=YES+cb.20210620-07-p0.en+FX; PREF=hl=" + (TextUtils.isEmpty(prefLang) ? "en" : prefLang) + "&f6=400";
        conn.setRequestProperty("Cookie", cookie);
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

    private String extractTextFromVtt(String vtt) {
        StringBuilder text = new StringBuilder();
        String[] lines = vtt.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("WEBVTT")) continue;
            if (line.matches("^\\d+$")) continue; // cue number
            if (line.contains("-->") || line.startsWith("X-TIMESTAMP-MAP")) continue;
            text.append(line).append(' ');
        }
        return text.toString().trim();
    }

    private String extractTextFromJson3(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray events = root.optJSONArray("events");
            if (events == null) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                JSONArray segs = ev.optJSONArray("segs");
                if (segs == null) continue;
                for (int j = 0; j < segs.length(); j++) {
                    JSONObject seg = segs.optJSONObject(j);
                    if (seg == null) continue;
                    String t = seg.optString("utf8", "");
                    if (!TextUtils.isEmpty(t)) sb.append(t);
                }
                sb.append(' ');
            }
            return sb.toString().trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private String parseCaptionResponse(String resp) {
        if (TextUtils.isEmpty(resp)) return null;
        String trimmed = resp.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"events\"")) {
            return extractTextFromJson3(trimmed);
        }
        if (trimmed.startsWith("WEBVTT")) {
            return extractTextFromVtt(trimmed);
        }
        if (trimmed.startsWith("<") && trimmed.contains("<text")) {
            return extractTextFromTimedText(trimmed);
        }
        // Unknown format
        return null;
    }
    
    private String cleanTranscript(String transcript) {
        if (TextUtils.isEmpty(transcript)) return transcript;
        
        // Clean up the transcript for better processing
        transcript = transcript.replaceAll("\\s+", " "); // Normalize whitespace
        transcript = transcript.replaceAll("\\[.*?\\]", ""); // Remove [Music], [Applause], etc.
        transcript = transcript.replaceAll("\\(.*?\\)", ""); // Remove (background noise), etc.
        
        // Limit transcript length per settings (0=unlimited)
        int max = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(
                com.liskovsoft.youtubeapi.app.AppService.instance().getContext()
        ).getMaxTranscriptChars();
        if (max > 0 && transcript.length() > max) {
            transcript = transcript.substring(0, max) + "... [transcript truncated]";
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

    private String callGemini(String prompt) throws IOException, JSONException {
        return callGemini(prompt, null);
    }
    
    private String callGemini(String prompt, String videoUrl) throws IOException, JSONException {
        // Check user's model preference from settings
        String userModel = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData
                .instance(com.liskovsoft.youtubeapi.app.AppService.instance().getContext())
                .getModel();
        
        android.util.Log.d("GeminiClient", "User selected model: " + userModel);
        
        if ("auto".equals(userModel)) {
            // Auto mode: Try fast model first, then fallback
            try {
                return callGeminiWithModel(prompt, videoUrl, MODEL);
            } catch (IOException e) {
                android.util.Log.w("GeminiClient", "Fast model failed, trying fallback: " + e.getMessage());
                return callGeminiWithModel(prompt, videoUrl, FALLBACK_MODEL);
            }
        } else {
            // Use specific model selected by user - no fallback
            android.util.Log.d("GeminiClient", "Using user-selected model (no fallback): " + userModel);
            return callGeminiWithModel(prompt, videoUrl, userModel);
        }
    }
    
    private String callGeminiWithModel(String prompt, String videoUrl, String model) throws IOException, JSONException {
        long startTime = System.currentTimeMillis();
        android.util.Log.d("GeminiClient", "Starting Gemini API call with model " + model + " at: " + startTime);
        
        JSONObject req = new JSONObject();
        JSONArray parts = new JSONArray();
        
        // OPTIMIZATION: Add video URL FIRST, then text (as recommended by Google)
        if (videoUrl != null && !TextUtils.isEmpty(videoUrl)) {
            JSONObject fileData = new JSONObject();
            fileData.put("fileUri", videoUrl);
            parts.put(new JSONObject().put("fileData", fileData));
            android.util.Log.d("GeminiClient", "Using optimized fileData approach for URL: " + videoUrl);
        }
        
        // Add text prompt AFTER video
        parts.put(new JSONObject().put("text", prompt));
        
        JSONObject content = new JSONObject().put("parts", parts);
        req.put("contents", new JSONArray().put(content));
        android.util.Log.d("GeminiClient", "Optimized request JSON: " + req.toString());

        byte[] payload = req.toString().getBytes(StandardCharsets.UTF_8);

        String apiUrl = String.format(API_URL_TEMPLATE, model) + apiKey;
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        
        // Aggressive timeouts for faster model, longer for fallback
        if (MODEL.equals(model)) {
            conn.setConnectTimeout(15000);  // 15s for fast model
            conn.setReadTimeout(45000);     // 45s for fast model
        } else {
            conn.setConnectTimeout(30000);  // 30s for fallback
            conn.setReadTimeout(120000);    // 2min for fallback
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("Gemini HTTP " + code + ":\n" + resp);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        android.util.Log.d("GeminiClient", "Gemini API call completed in: " + duration + "ms");
        
        JSONObject json = new JSONObject(resp);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject contentResp = cand.optJSONObject("content");
            if (contentResp != null) {
                JSONArray partsResp = contentResp.optJSONArray("parts");
                if (partsResp != null && partsResp.length() > 0) {
                    String summaryText = partsResp.getJSONObject(0).optString("text", resp);
                    // Store the model used for this response
                    lastUsedModel = model;
                    // Parse usage metadata when available
                    try {
                        JSONObject usage = json.optJSONObject("usageMetadata");
                        if (usage != null) {
                            lastPromptTokens = usage.has("promptTokenCount") ? usage.optInt("promptTokenCount") : null;
                            lastCandidatesTokens = usage.has("candidatesTokenCount") ? usage.optInt("candidatesTokenCount") : null;
                            lastTotalTokens = usage.has("totalTokenCount") ? usage.optInt("totalTokenCount") : null;
                        } else {
                            lastPromptTokens = lastCandidatesTokens = lastTotalTokens = null;
                        }
                    } catch (Throwable ignore) {
                        lastPromptTokens = lastCandidatesTokens = lastTotalTokens = null;
                    }
                    return summaryText;
                }
            }
        }
        lastUsedModel = model;
        // Reset usage if not found
        lastPromptTokens = lastCandidatesTokens = lastTotalTokens = null;
        return resp;
    }

    private static String[] splitTranscript(String transcript, int chunkLen) {
        if (transcript == null) return new String[0];
        int len = transcript.length();
        if (len <= chunkLen) return new String[] { transcript };
        int parts = (len + chunkLen - 1) / chunkLen;
        String[] out = new String[parts];
        for (int i = 0, pos = 0; i < parts; i++, pos += chunkLen) {
            int end = Math.min(pos + chunkLen, len);
            out[i] = transcript.substring(pos, end);
        }
        return out;
    }


    private String performFactCheck(String summary, String title, String author, String videoId) throws IOException, JSONException {
        android.util.Log.d("GeminiClient", "🔍 Starting performFactCheck for: " + title);
        
        String factCheckPrompt = "Fact-check the following video summary using web search. " +
                "Focus on verifying key claims, statistics, dates, and factual assertions. " +
                "Format your response as '**Fact Check Results:**' followed by bullet points indicating " +
                "which claims were verified, any corrections needed, or if no verification was possible.\n\n" +
                "Video: " + title + (author != null ? " by " + author : "") + "\n\n" +
                "Summary to fact-check:\n" + summary;

        android.util.Log.d("GeminiClient", "📝 Calling Gemini with web search for fact check");
        String result = callGeminiWithWebSearch(factCheckPrompt);
        android.util.Log.d("GeminiClient", "🔍 Fact check complete, result: " + (result != null ? result.length() + " chars" : "NULL"));
        return result;
    }

    private String callGeminiWithWebSearch(String prompt) throws IOException, JSONException {
        android.util.Log.d("GeminiClient", "🌐 callGeminiWithWebSearch starting");
        
        String userModel = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData
                .instance(com.liskovsoft.youtubeapi.app.AppService.instance().getContext())
                .getModel();
        
        // For fact checking with web search, use stable models that support tools
        String model;
        if ("auto".equals(userModel)) {
            model = "gemini-2.5-flash"; // Use stable model for tools support
        } else if ("gemini-2.0-flash-exp".equals(userModel)) {
            model = "gemini-2.5-flash"; // Fallback to stable for experimental model
        } else {
            model = userModel;
        }
        
        android.util.Log.d("GeminiClient", "🤖 Using model for fact check: " + model + " (user model: " + userModel + ")");
        
        JSONObject req = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        
        JSONObject content = new JSONObject().put("parts", parts);
        req.put("contents", new JSONArray().put(content));
        
        // Enable web search for fact checking
        JSONObject tools = new JSONObject();
        JSONArray toolsArray = new JSONArray();
        JSONObject webSearchTool = new JSONObject();
        webSearchTool.put("googleSearch", new JSONObject());
        toolsArray.put(webSearchTool);
        req.put("tools", toolsArray);

        byte[] payload = req.toString().getBytes(StandardCharsets.UTF_8);

        String apiUrl = String.format(API_URL_TEMPLATE, model) + apiKey;
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            android.util.Log.w("GeminiClient", "Fact check HTTP " + code + ": " + resp);
            // Store a concise reason for the caller to surface
            String snippet = resp != null && resp.length() > 200 ? resp.substring(0, 200) + "…" : resp;
            lastFactCheckError = "HTTP " + code + (snippet != null ? (": " + snippet) : "");
            return null;
        }

        JSONObject json = new JSONObject(resp);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject contentResp = cand.optJSONObject("content");
            if (contentResp != null) {
                JSONArray partsResp = contentResp.optJSONArray("parts");
                if (partsResp != null && partsResp.length() > 0) {
                    return partsResp.getJSONObject(0).optString("text", null);
                }
            }
        }
        lastFactCheckError = "no text candidates returned";
        return null;
    }

    // Reusable transcript helpers for other AI clients
    public static String fetchTranscriptForSummary(android.content.Context ctx, String videoId) {
        try {
            GeminiClient g = new GeminiClient(ctx);
            TranscriptResult tr = g.fetchTranscriptWithSource(videoId);
            return tr != null ? tr.transcript : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hasOfficialCC(android.content.Context ctx, String videoId) {
        try {
            GeminiClient g = new GeminiClient(ctx);
            return g.hasOfficialTrack(videoId);
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Summarize a list of viewer comments. Keeps the output concise and focused on themes.
     */
    public String summarizeComments(String videoTitle, String author, String videoId, java.util.List<String> comments, int analyzedCount) throws IOException, JSONException {
        if (comments == null || comments.isEmpty()) {
            return null;
        }

        // Trim each comment to reduce prompt size
        StringBuilder sb = new StringBuilder();
        android.util.Log.d("GeminiClient", "Summarizing comments: count=" + comments.size() + ", analyzed=" + analyzedCount);
        sb.append("You are summarizing viewer comments for an Android TV overlay.\n");
        sb.append("Provide a concise 'Comments Summary' with: common themes, consensus, notable insights, disagreements, sentiment, and useful viewer tips.\n");
        sb.append("Avoid quoting long texts; no personal data; be neutral.\n");
        sb.append("Keep it brief and skimmable (bullet points preferred).\n\n");
        if (!android.text.TextUtils.isEmpty(videoTitle)) sb.append("Video: ").append(videoTitle).append("\n");
        if (!android.text.TextUtils.isEmpty(author)) sb.append("Channel: ").append(author).append("\n");
        if (!android.text.TextUtils.isEmpty(videoId)) sb.append("VideoID: ").append(videoId).append("\n\n");

        sb.append("Sample of top comments (truncated):\n");
        int perLen = 220; // hard cap per comment snippet (keep prompt tighter)
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
        android.util.Log.d("GeminiClient", "Comments prompt size chars=" + prompt.length());
        return callGemini(prompt);
    }
}

