package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lightweight on-device Text-to-Speech manager for speaking AI summaries.
 * - Fast defaults (rate ~1.7, pitch ~1.05)
 * - Picks a low-latency, high-quality voice when possible
 * - Mutes player audio while speaking and restores afterward
 */
public class TtsManager {
    public interface Listener {
        void onStart();
        void onDone();
        void onError(String error);
    }

    private static volatile TtsManager sInstance;

    public static TtsManager instance(Context context) {
        if (sInstance == null) {
            synchronized (TtsManager.class) {
                if (sInstance == null) sInstance = new TtsManager(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private final Context appContext;
    private TextToSpeech tts;
    private boolean initialized;
    private final List<Listener> listeners = new ArrayList<>();
    private float defaultRate = 1.7f;
    private float defaultPitch = 1.05f;
    private boolean speaking;
    private Float prevPlayerVolume; // null if not changed

    private TtsManager(Context context) {
        this.appContext = context;
        initTts();
    }

    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(appContext, status -> {
            initialized = (status == TextToSpeech.SUCCESS);
            if (initialized) {
                tryConfigureDefaults();
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                speaking = true;
                notifyStart();
            }
            @Override public void onDone(String utteranceId) {
                speaking = false;
                restorePlayerVolumeIfNeeded();
                notifyDone();
            }
            @Override public void onError(String utteranceId) {
                speaking = false;
                restorePlayerVolumeIfNeeded();
                notifyError("TTS error");
            }
            @Override public void onError(String utteranceId, int errorCode) {
                speaking = false;
                restorePlayerVolumeIfNeeded();
                notifyError("TTS error: " + errorCode);
            }
        });
        if (Build.VERSION.SDK_INT >= 21) {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build());
        }
    }

    private void tryConfigureDefaults() {
        try {
            tts.setSpeechRate(defaultRate);
            tts.setPitch(defaultPitch);
            // Pick an English, low-latency high-quality voice when possible
            if (Build.VERSION.SDK_INT >= 21) {
                for (android.speech.tts.Voice v : tts.getVoices()) {
                    if (v == null || v.getLocale() == null) continue;
                    Locale loc = v.getLocale();
                    String lang = (loc.getLanguage() != null) ? loc.getLanguage().toLowerCase(Locale.US) : "";
                    boolean isEnglish = lang.startsWith("en");
                    boolean highQuality = v.getQuality() >= android.speech.tts.Voice.QUALITY_HIGH;
                    boolean lowLatency = v.getLatency() <= android.speech.tts.Voice.LATENCY_LOW;
                    if (isEnglish && highQuality && lowLatency && !v.isNetworkConnectionRequired()) {
                        tts.setVoice(v);
                        break;
                    }
                }
            } else {
                tts.setLanguage(Locale.getDefault());
            }
        } catch (Throwable ignored) {}
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    /**
     * Speak the given text quickly with a light tone. If a player is active, it mutes it while speaking.
     */
    public void speak(CharSequence text) {
        if (text == null) return;
        String s = text.toString().trim();
        if (TextUtils.isEmpty(s)) return;
        initTts();

        // Apply current rate from settings (if available)
        try {
            float rate = GeminiData.instance(appContext).getTtsRate();
            if (rate != defaultRate) {
                defaultRate = rate;
                if (tts != null) tts.setSpeechRate(defaultRate);
            }
        } catch (Throwable ignored) {}

        // Mute player while speaking (keep video playing silently)
        mutePlayerIfNeeded();

        // Split into safe chunks for TTS
        List<String> chunks = chunkText(s, 3800);
        // Flush any previous queue and speak
        int queueMode = TextToSpeech.QUEUE_FLUSH;
        for (int i = 0; i < chunks.size(); i++) {
            String id = UUID.randomUUID().toString();
            if (Build.VERSION.SDK_INT >= 21) {
                tts.speak(chunks.get(i), queueMode, null, id);
            } else {
                //noinspection deprecation
                tts.speak(chunks.get(i), queueMode, null);
            }
            queueMode = TextToSpeech.QUEUE_ADD;
        }
    }

    public void stop() {
        try {
            if (tts != null) tts.stop();
        } catch (Throwable ignored) {}
        speaking = false;
        restorePlayerVolumeIfNeeded();
    }

    public void setRate(float rate) {
        defaultRate = rate;
        if (tts != null) tts.setSpeechRate(rate);
    }

    public void setPitch(float pitch) {
        defaultPitch = pitch;
        if (tts != null) tts.setPitch(pitch);
    }

    private void mutePlayerIfNeeded() {
        try {
            PlaybackPresenter pp = PlaybackPresenter.instance(appContext);
            if (pp != null && pp.getPlayer() != null) {
                float current = pp.getPlayer().getVolume();
                prevPlayerVolume = current;
                pp.getPlayer().setVolume(0f);
            } else {
                prevPlayerVolume = null;
            }
        } catch (Throwable ignored) {
            prevPlayerVolume = null;
        }
    }

    private void restorePlayerVolumeIfNeeded() {
        try {
            if (prevPlayerVolume != null) {
                PlaybackPresenter pp = PlaybackPresenter.instance(appContext);
                if (pp != null && pp.getPlayer() != null) {
                    pp.getPlayer().setVolume(prevPlayerVolume);
                }
            }
        } catch (Throwable ignored) {
            // NOP
        } finally {
            prevPlayerVolume = null;
        }
    }

    private static List<String> chunkText(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        // Prefer sentence boundaries
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (buf.length() + s.length() + 1 > maxLen) {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                if (s.length() > maxLen) {
                    // Hard split very long sentences
                    int i = 0;
                    while (i < s.length()) {
                        int end = Math.min(i + maxLen, s.length());
                        out.add(s.substring(i, end));
                        i = end;
                    }
                } else {
                    buf.append(s);
                }
            } else {
                if (buf.length() > 0) buf.append(' ');
                buf.append(s);
            }
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out;
    }

    private void notifyStart() { for (Listener l : new ArrayList<>(listeners)) l.onStart(); }
    private void notifyDone() { for (Listener l : new ArrayList<>(listeners)) l.onDone(); }
    private void notifyError(String e) { for (Listener l : new ArrayList<>(listeners)) l.onError(e); }
}
