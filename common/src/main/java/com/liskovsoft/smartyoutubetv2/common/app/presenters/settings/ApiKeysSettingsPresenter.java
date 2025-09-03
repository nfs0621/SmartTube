package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ApiKeyPrefs;

import java.util.ArrayList;
import java.util.List;

/** Manage API keys: view masked status, clear, and launch pairing. */
public class ApiKeysSettingsPresenter {
    private final Context ctx;

    private ApiKeysSettingsPresenter(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public static ApiKeysSettingsPresenter instance(Context c) {
        return new ApiKeysSettingsPresenter(c);
    }

    public void show() {
        AppDialogPresenter dlg = AppDialogPresenter.instance(ctx);
        ApiKeyPrefs prefs = ApiKeyPrefs.instance(ctx);

        String openai = prefs.getOpenAIKey();
        String gemini = prefs.getGeminiKey();

        // Status section (masked keys)
        List<OptionItem> status = new ArrayList<>();
        status.add(UiOptionItem.from("OpenAI key: " + masked(openai)));
        status.add(UiOptionItem.from("Gemini key: " + masked(gemini)));
        dlg.appendStringsCategory("Status", status);

        // Actions section
        List<OptionItem> actions = new ArrayList<>();
        actions.add(UiOptionItem.from("Pair from phone (QR)", opt -> launchPairing()));
        if (!TextUtils.isEmpty(openai)) {
            actions.add(UiOptionItem.from("Clear OpenAI key", opt -> {
                prefs.clearOpenAIKey();
                Toast.makeText(ctx, "OpenAI key cleared", Toast.LENGTH_SHORT).show();
                show();
            }));
        }
        if (!TextUtils.isEmpty(gemini)) {
            actions.add(UiOptionItem.from("Clear Gemini key", opt -> {
                prefs.clearGeminiKey();
                Toast.makeText(ctx, "Gemini key cleared", Toast.LENGTH_SHORT).show();
                show();
            }));
        }
        dlg.appendStringsCategory("Actions", actions);

        dlg.showDialog("API Keys");
    }

    private void launchPairing() {
        try {
            Intent i = new Intent();
            i.setClassName(ctx.getPackageName(), "com.liskovsoft.smartyoutubetv2.tv.ui.apikey.PairApiKeyActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(ctx, "Pairing screen not available", Toast.LENGTH_SHORT).show();
        }
    }

    private static String masked(String key) {
        if (TextUtils.isEmpty(key)) return "(not set)";
        int n = key.length();
        String last4 = n >= 4 ? key.substring(n - 4) : key;
        return "•••• " + last4;
    }
}
