package com.liskovsoft.smartyoutubetv2.tv.ui.push;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.pairing.DevicePushHttpServer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class PushToDeviceFragment extends GuidedStepSupportFragment {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_LINK = "link";
    public static final String EXTRA_DESC = "desc";
    public static final String EXTRA_PUBLISHED = "published";
    public static final String EXTRA_DURATION = "duration";

    private static final int SERVER_PORT = 8080;
    private static final int ACTION_CLOSE = 1;
    private String token;
    private String url;
    private Bitmap qr;
    private DevicePushHttpServer server;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = com.liskovsoft.smartyoutubetv2.tv.pairing.PairingHttpServer.newToken();
        url = buildUrl();
        try { qr = generateQr(url, 520); } catch (Throwable ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        if (server == null) startServer();
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.push_to_device_title);
        String desc = getString(R.string.push_to_device_desc) + "\n\n" + url;
        BitmapDrawable icon = (qr != null) ? new BitmapDrawable(getResources(), qr) : null;
        return new GuidanceStylist.Guidance(title, desc, "", icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder().id(ACTION_CLOSE).title(getString(R.string.back_to_summary)).build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_CLOSE) {
            if (getActivity() != null) getActivity().finish();
        }
    }

    private void startServer() {
        stopServer();
        DevicePushHttpServer.Data data = new DevicePushHttpServer.Data();
        Bundle b = getActivity() != null ? getActivity().getIntent().getExtras() : null;
        if (b != null) {
            data.title = b.getString(EXTRA_TITLE);
            data.author = b.getString(EXTRA_AUTHOR);
            data.link = b.getString(EXTRA_LINK);
            data.description = b.getString(EXTRA_DESC);
            data.published = b.getString(EXTRA_PUBLISHED);
            data.duration = b.getString(EXTRA_DURATION);
        }
        server = new DevicePushHttpServer(SERVER_PORT, token, 10 * 60_000L, data);
        try { server.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false); } catch (Exception ignored) {}
    }

    private void stopServer() {
        if (server != null) { try { server.stop(); } catch (Throwable ignored) {} server = null; }
    }

    private String buildUrl() {
        String ip = findLocalIPv4();
        return ip != null ? ("http://" + ip + ":" + SERVER_PORT + "/push?t=" + token) : "http://<tv-ip>:8080/push";
    }

    private static String findLocalIPv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Bitmap generateQr(String text, int size) throws Exception {
        com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
        com.google.zxing.common.BitMatrix bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? black : white;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }
}
