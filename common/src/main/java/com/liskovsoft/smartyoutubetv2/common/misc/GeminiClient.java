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
    private final String prefLang;
    private final boolean debug;

    public GeminiClient(Context context) {
        this.apiKey = loadApiKey(context);
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(context);
        this.prefLang = gd.getPreferredLanguage();
        this.debug = gd.isDebugLogging();
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
        
        boolean officialAvailable = hasOfficialTrack(videoId);
        String prompt = buildPrompt(title, author, videoId, detailLevel, transcript, transcriptSource, officialAvailable);
        try {
            return callGemini(prompt);
        } catch (IOException e) {
            // Fallback: chunked summarization to avoid timeouts on huge transcripts
            if (!TextUtils.isEmpty(transcript)) {
                String[] chunks = splitTranscript(transcript, 12000);
                StringBuilder partials = new StringBuilder();
                for (int i = 0; i < chunks.length; i++) {
                    String chunkPrompt = buildPrompt(title + " (chunk " + (i+1) + "/" + chunks.length + ")", author, videoId, detailLevel, chunks[i], transcriptSource, officialAvailable);
                    String part = callGemini(chunkPrompt);
                    partials.append("\n[Chunk ").append(i+1).append("]\n").append(part).append("\n");
                }
                String combine = "Summarize the following partial summaries into a single cohesive summary with the same format and header requirements.\n\n" + partials;
                return callGemini(combine);
            }
            throw e;
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
        JSONObject req = new JSONObject();
        JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
        JSONObject content = new JSONObject().put("parts", parts);
        req.put("contents", new JSONArray().put(content));

        byte[] payload = req.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + apiKey).openConnection();
        conn.setRequestMethod("POST");
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
            throw new IOException("Gemini HTTP " + code + ":\n" + resp);
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
}

