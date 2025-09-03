package com.liskovsoft.smartyoutubetv2.tv.ui.apikey;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.liskovsoft.smartyoutubetv2.common.prefs.ApiKeyPrefs;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.pairing.PairingHttpServer;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class PairApiKeyFragment extends GuidedStepSupportFragment implements PairingHttpServer.Listener {
    private static final int ACTION_DONE = 1;
    private static final int SERVER_PORT = 8080;
    private PairingHttpServer mServer;
    private String mToken;
    private String mPin;
    private String mUrl;
    private Bitmap mQr;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Pre-generate token/pin/url so we can render QR in guidance
        if (mToken == null) mToken = PairingHttpServer.newToken();
        if (mPin == null) mPin = PairingHttpServer.newPin();
        mUrl = buildUrlWithToken();
        try { mQr = generateQr(mUrl, 480); } catch (Throwable ignored) {}
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Ensure UI updates happen when views are ready
        if (mToken != null) {
            updateTitleWithEndpoint();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mServer == null) {
            startServer(requireContext());
        } else {
            updateTitleWithEndpoint();
        }
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void startServer(Context ctx) {
        stopServer();
        if (mToken == null) mToken = PairingHttpServer.newToken();
        if (mPin == null) mPin = PairingHttpServer.newPin();
        mServer = new PairingHttpServer(SERVER_PORT, mToken, mPin, 10 * 60_000L, this);
        try {
            mServer.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            updateTitleWithEndpoint();
        } catch (Exception e) {
            setTitle("Server error: " + e.getMessage());
        }
    }

    private void stopServer() {
        if (mServer != null) {
            try { mServer.stop(); } catch (Throwable ignored) {}
            mServer = null;
        }
    }

    private void updateTitleWithEndpoint() {
        mUrl = buildUrlWithToken();
        setTitle(mUrl + "\nPIN: " + mPin);
        try {
            mQr = generateQr(mUrl, 480);
            if (getGuidanceStylist() != null) {
                if (getGuidanceStylist().getIconView() != null && mQr != null) {
                    getGuidanceStylist().getIconView().setImageBitmap(mQr);
                }
                if (getGuidanceStylist().getDescriptionView() != null) {
                    String desc = getString(R.string.api_key_pairing_desc) + "\n\n" + mUrl + "\nPIN: " + mPin;
                    getGuidanceStylist().getDescriptionView().setText(desc);
                }
            }
        } catch (Throwable ignored) {}
    }

    private String buildUrlWithToken() {
        String ip = findLocalIPv4();
        return ip != null ? ("http://" + ip + ":" + SERVER_PORT + "/pair?t=" + (mToken != null ? mToken : "")) : "http://<tv-ip>:8080/pair";
    }

    private static android.graphics.Bitmap generateQr(String text, int size) throws Exception {
        com.google.zxing.Writer writer = new com.google.zxing.qrcode.QRCodeWriter();
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
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }

    private void setTitle(String text) {
        GuidanceStylist stylist = getGuidanceStylist();
        if (stylist != null && stylist.getTitleView() != null) {
            stylist.getTitleView().setText(text);
        }
    }

    @Override
    public void onApiKeyReceived(String provider, String apiKey) {
        // Persist and finish on main thread to avoid crashes
        new Handler(Looper.getMainLooper()).post(() -> {
            Context c = getContext();
            if (c != null) {
                ApiKeyPrefs.instance(c).setApiKey(provider, apiKey);
                Toast.makeText(c, getString(R.string.api_key_saved_toast), Toast.LENGTH_SHORT).show();
            }
            stopServer();
            if (getActivity() != null) getActivity().finish();
        });
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.api_key_pairing_title);
        String desc = getString(R.string.api_key_pairing_desc) + "\n\n" + (mUrl != null ? mUrl : "http://<tv-ip>:8080/pair") + "\nPIN: " + (mPin != null ? mPin : "------");
        BitmapDrawable icon = (mQr != null) ? new BitmapDrawable(getResources(), mQr) : null;
        return new GuidanceStylist.Guidance(title, desc, "", icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder()
                .id(ACTION_DONE)
                .title(getString(R.string.dialog_close))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_DONE) {
            if (getActivity() != null) getActivity().finish();
        }
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
}
