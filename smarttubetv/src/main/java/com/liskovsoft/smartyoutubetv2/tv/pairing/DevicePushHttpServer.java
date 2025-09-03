package com.liskovsoft.smartyoutubetv2.tv.pairing;

import fi.iki.elonen.NanoHTTPD;

import java.util.Map;

/**
 * Simple local HTTP server that serves a read-only page with video metadata and optional summary.
 * Endpoint: GET /push?t=<token>
 */
public class DevicePushHttpServer extends NanoHTTPD {
    public static class Data {
        public String title;
        public String author;
        public String link;
        public String description; // optional summary text
        public String published;
        public String duration;
    }

    private final String token;
    private final long expiresAtMs;
    private final Data data;

    public DevicePushHttpServer(int port, String token, long ttlMs, Data data) {
        super(port);
        this.token = token;
        this.expiresAtMs = System.currentTimeMillis() + Math.max(60_000L, ttlMs);
        this.data = data;
    }

    private boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMs;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (isExpired()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Session expired");
        }
        String uri = session.getUri();
        if (Method.GET.equals(session.getMethod()) && "/push".equals(uri)) {
            Map<String, String> params = session.getParms();
            String t = params.get("t");
            if (t == null || !t.equals(token)) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid token");
            }
            return serveHtml();
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
    }

    private Response serveHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>").append(escape(data.title)).append("</title>");
        sb.append("<style>body{font-family:sans-serif;margin:20px;line-height:1.5} .meta{color:#555;font-size:14px} .box{border:1px solid #eee;padding:12px;border-radius:8px} a{word-break:break-all}</style>");
        sb.append("</head><body>");
        sb.append("<h2>").append(escape(data.title)).append("</h2>");
        if (data.author != null) sb.append("<div class='meta'>").append(escape(data.author)).append("</div>");
        if (data.published != null || data.duration != null) {
            sb.append("<div class='meta'>");
            if (data.published != null) sb.append("Published: ").append(escape(data.published)).append(" ");
            if (data.duration != null) sb.append(" • Duration: ").append(escape(data.duration));
            sb.append("</div>");
        }
        if (data.link != null) {
            sb.append("<p><a href='").append(escapeAttr(data.link)).append("' target='_blank'>").append(escape(data.link)).append("</a></p>");
        }
        if (data.description != null && !data.description.isEmpty()) {
            sb.append("<div class='box'><pre style='white-space:pre-wrap;'>").append(escape(data.description)).append("</pre></div>");
        }
        sb.append("<p style='color:#888;font-size:13px'>Served locally by SmartTube. You can close this tab after reading.</p>");
        sb.append("</body></html>");
        return newFixedLengthResponse(Response.Status.OK, "text/html", sb.toString());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private static String escapeAttr(String s) { return escape(s).replace("\"","&quot;"); }
}

