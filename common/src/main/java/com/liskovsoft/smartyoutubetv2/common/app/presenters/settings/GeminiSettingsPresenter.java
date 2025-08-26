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

        // Detail level setting
        List<OptionItem> detailLevels = new ArrayList<>();
        String currentLevel = data.getDetailLevel();
        detailLevels.add(UiOptionItem.from(context.getString(R.string.gemini_detail_level_concise), 
            opt -> data.setDetailLevel("concise"), "concise".equals(currentLevel)));
        detailLevels.add(UiOptionItem.from(context.getString(R.string.gemini_detail_level_moderate), 
            opt -> data.setDetailLevel("moderate"), "moderate".equals(currentLevel)));
        detailLevels.add(UiOptionItem.from(context.getString(R.string.gemini_detail_level_detailed), 
            opt -> data.setDetailLevel("detailed"), "detailed".equals(currentLevel)));
        dlg.appendRadioCategory(context.getString(R.string.gemini_detail_level_title), detailLevels);

        // Source mode setting
        List<OptionItem> modes = new ArrayList<>();
        String currentMode = data.getMode();
        modes.add(UiOptionItem.from(context.getString(R.string.gemini_source_mode_url),
                opt -> data.setMode("url"),
                "url".equalsIgnoreCase(currentMode)));
        modes.add(UiOptionItem.from(context.getString(R.string.gemini_source_mode_transcript),
                opt -> data.setMode("transcript"),
                "transcript".equalsIgnoreCase(currentMode)));
        dlg.appendRadioCategory(context.getString(R.string.gemini_source_mode_title), modes);

        // Transcript content size / tokens
        List<OptionItem> sizes = new ArrayList<>();
        int currentMax = data.getMaxTranscriptChars();
        sizes.add(UiOptionItem.from("Full transcript", opt -> data.setMaxTranscriptChars(0), currentMax == 0));
        sizes.add(UiOptionItem.from("~4k chars", opt -> data.setMaxTranscriptChars(4000), currentMax == 4000));
        sizes.add(UiOptionItem.from("~12k chars", opt -> data.setMaxTranscriptChars(12000), currentMax == 12000));
        dlg.appendRadioCategory("Transcript length", sizes);

        // Preferred language
        List<OptionItem> langs = new ArrayList<>();
        String curLang = data.getPreferredLanguage();
        langs.add(UiOptionItem.from("English", opt -> data.setPreferredLanguage("en"), "en".equalsIgnoreCase(curLang)));
        dlg.appendRadioCategory("Preferred transcript language", langs);

        // Verbose logging
        List<OptionItem> debug = new ArrayList<>();
        debug.add(UiOptionItem.from("Verbose logging", opt -> data.setDebugLogging(opt.isSelected()), data.isDebugLogging()));
        dlg.appendCheckedCategory("Debug", debug);

        dlg.showDialog(context.getString(R.string.gemini_settings_title), null);
    }
}

