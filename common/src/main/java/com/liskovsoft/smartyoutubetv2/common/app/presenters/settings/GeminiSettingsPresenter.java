package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData;

import java.util.ArrayList;
import java.util.List;

public class GeminiSettingsPresenter {
    private final Context context;
    private final GeminiData data;

    private GeminiSettingsPresenter(Context ctx) {
        this.context = ctx;
        this.data = GeminiData.instance(ctx);
    }

    public static GeminiSettingsPresenter instance(Context ctx) {
        return new GeminiSettingsPresenter(ctx);
    }

    public void show() {
        AppDialogPresenter dlg = AppDialogPresenter.instance(context);

        // Enable/disable
        List<OptionItem> enabled = new ArrayList<>();
        enabled.add(UiOptionItem.from(context.getString(R.string.gemini_enable_summaries), opt -> data.setEnabled(opt.isSelected()), data.isEnabled()));
        dlg.appendCheckedCategory(context.getString(R.string.gemini_category_title), enabled);

        // Delay options
        List<OptionItem> delays = new ArrayList<>();
        int[] values = {3000, 5000, 8000};
        String[] titles = {
                context.getString(R.string.gemini_delay_seconds, 3),
                context.getString(R.string.gemini_delay_seconds, 5),
                context.getString(R.string.gemini_delay_seconds, 8)
        };
        int current = data.getDelayMs();
        for (int i = 0; i < values.length; i++) {
            final int ms = values[i];
            delays.add(UiOptionItem.from(titles[i], opt -> data.setDelayMs(ms), current == ms));
        }
        dlg.appendRadioCategory(context.getString(R.string.gemini_delay_title), delays);

        dlg.showDialog(context.getString(R.string.gemini_settings_title), null);
    }
}

