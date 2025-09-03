package com.liskovsoft.smartyoutubetv2.tv.pairing;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal embedded HTTP server to accept an API key over LAN.
 * Endpoints:
 *  - GET /pair -> HTML form
 *  - POST /submit -> validate token + PIN, accept apiKey
 */
public class PairingHttpServer extends NanoHTTPD {
    public interface Listener {
        void onApiKeyReceived(String provider, String apiKey);
    }

    private final String mToken;
    private final String mPin;
    private final long mExpiresAtMs;
    private final Listener mListener;
    private volatile boolean mCompleted;

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String newPin() {
        int n = (int)(Math.random() * 1_000_000); // 0..999999
        return String.format("%06d", n);
    }

    public PairingHttpServer(int port, String token, String pin, long ttlMs, Listener listener) {
        super(port);
        this.mToken = token;
        this.mPin = pin;
        this.mExpiresAtMs = System.currentTimeMillis() + Math.max(60_000L, ttlMs);
        this.mListener = listener;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > mExpiresAtMs;
    }

    public boolean isCompleted() {
        return mCompleted;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            if (isExpired()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Session expired.");
            }

            String uri = session.getUri();
            Method method = session.getMethod();

            if (Method.GET.equals(method) && "/pair".equals(uri)) {
                return servePairForm();
            }

            if (Method.POST.equals(method) && "/submit".equals(uri)) {
                return handleSubmit(session);
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
        }
    }

    private Response servePairForm() {
        String html = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>SmartTube Pairing</title>"
                + "<style>body{font-family:sans-serif;margin:24px;max-width:720px} textarea{width:100%;height:180px} input{font-size:18px} .pin{font-weight:bold} fieldset{border:1px solid #ddd;padding:8px}</style>"
                + "</head><body>"
                + "<h2>Enter API Key</h2>"
                + "<p>PIN: <span class='pin'>" + mPin + "</span></p>"
                + "<form method='POST' action='/submit'>"
                + "<input type='hidden' name='token' value='" + mToken + "'>"
                + "<fieldset><legend>Provider</legend>"
                + "<label><input type='radio' name='provider' value='openai' checked> OpenAI</label><br>"
                + "<label><input type='radio' name='provider' value='gemini'> Google (Gemini)</label>"
                + "</fieldset>"
                + "<p><label>PIN<br><input name='pin' required pattern='\\d{6}' maxlength='6'></label></p>"
                + "<p><label>API Key<br><textarea name='apiKey' required placeholder='Paste your API key here'></textarea></label></p>"
                + "<p><button type='submit'>Submit</button></p>"
                + "</form>"
                + "<p>This page is served locally by your TV app. It will close automatically after success.</p>"
                + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response handleSubmit(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new java.util.HashMap<>();
        session.parseBody(files);
        Map<String, String> params = session.getParms();

        String token = params.get("token");
        String pin = params.get("pin");
        String apiKey = params.get("apiKey");
        String provider = params.get("provider");

        if (token == null || pin == null || apiKey == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing fields");
        }
        if (!mToken.equals(token) || !mPin.equals(pin)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid token or PIN");
        }

        mCompleted = true;
        if (mListener != null) {
            try { mListener.onApiKeyReceived(provider != null ? provider : "openai", apiKey); } catch (Throwable ignored) {}
        }

        String html = "<!doctype html><html><head><meta charset='utf-8'><title>Done</title>"
                + "<script>setTimeout(function(){window.close();},1000);</script>"
                + "<style>body{font-family:sans-serif;margin:24px}</style>"
                + "</head><body><h3>API key received.</h3><p>You can return to the TV.</p></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }
}
