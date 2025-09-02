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
        enabled.add(UiOptionItem.from("Email summaries", opt -> data.setEmailSummariesEnabled(opt.isSelected()), data.isEmailSummariesEnabled()));
        enabled.add(UiOptionItem.from("Fact check summaries (uses web search)", opt -> data.setFactCheckEnabled(opt.isSelected()), data.isFactCheckEnabled()));
        enabled.add(UiOptionItem.from("Auto mark as watched on summary", opt -> data.setMarkAsWatchedEnabled(opt.isSelected()), data.isMarkAsWatchedEnabled()));
        enabled.add(UiOptionItem.from("Summarize comments in overlay", opt -> data.setCommentsSummaryEnabled(opt.isSelected()), data.isCommentsSummaryEnabled()));
        dlg.appendCheckedCategory(context.getString(R.string.gemini_category_title), enabled);

        // Delay options removed (no auto-summary)

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

        // Provider selection
        List<OptionItem> providers = new ArrayList<>();
        String curProvider = data.getProvider();
        providers.add(UiOptionItem.from("OpenAI", opt -> data.setProvider("openai"), "openai".equalsIgnoreCase(curProvider)));
        providers.add(UiOptionItem.from("Google (Gemini)", opt -> data.setProvider("gemini"), "gemini".equalsIgnoreCase(curProvider)));
        dlg.appendRadioCategory("AI Provider", providers);

        // AI Model selection (depends on provider)
        if ("openai".equalsIgnoreCase(curProvider)) {
            List<OptionItem> oModels = new ArrayList<>();
            String c = data.getOpenAIModel();
            oModels.add(UiOptionItem.from("GPT‑5 Mini (default)", opt -> data.setOpenAIModel("gpt5-mini"), "gpt5-mini".equalsIgnoreCase(c)));
            oModels.add(UiOptionItem.from("GPT‑5", opt -> data.setOpenAIModel("gpt5"), "gpt5".equalsIgnoreCase(c)));
            oModels.add(UiOptionItem.from("GPT‑5 Nano", opt -> data.setOpenAIModel("gpt5-nano"), "gpt5-nano".equalsIgnoreCase(c)));
            dlg.appendRadioCategory("AI Model", oModels);

            // Advanced: custom model id override
            List<OptionItem> oAdvanced = new ArrayList<>();
            String currentCustom = data.getOpenAICustomModel();
            oAdvanced.add(UiOptionItem.from(
                    "Set custom OpenAI model id" + (currentCustom != null && !currentCustom.isEmpty() ? " (" + currentCustom + ")" : ""),
                    opt -> showOpenAICustomModelDialog(context, currentCustom),
                    false));
            if (currentCustom != null && !currentCustom.isEmpty()) {
                oAdvanced.add(UiOptionItem.from("Clear custom model id", opt -> data.setOpenAICustomModel(null), false));
            }
            dlg.appendStringsCategory("Advanced", oAdvanced);
        } else {
            List<OptionItem> gModels = new ArrayList<>();
            String currentModel = data.getModel();
            gModels.add(UiOptionItem.from("Auto (2.0-flash-exp → 2.5-flash)",
                    opt -> data.setModel("auto"),
                    "auto".equals(currentModel)));
            gModels.add(UiOptionItem.from("Gemini 2.0 Flash Experimental",
                    opt -> data.setModel("gemini-2.0-flash-exp"),
                    "gemini-2.0-flash-exp".equals(currentModel)));
            gModels.add(UiOptionItem.from("Gemini 2.5 Flash",
                    opt -> data.setModel("gemini-2.5-flash"),
                    "gemini-2.5-flash".equals(currentModel)));
            gModels.add(UiOptionItem.from("Gemini 1.5 Flash",
                    opt -> data.setModel("gemini-1.5-flash"),
                    "gemini-1.5-flash".equals(currentModel)));
            gModels.add(UiOptionItem.from("Gemini 1.5 Pro",
                    opt -> data.setModel("gemini-1.5-pro"),
                    "gemini-1.5-pro".equals(currentModel)));
            gModels.add(UiOptionItem.from("Gemini 1.0 Pro",
                    opt -> data.setModel("gemini-1.0-pro"),
                    "gemini-1.0-pro".equals(currentModel)));
            dlg.appendRadioCategory("AI Model", gModels);
        }

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

        // Comments summary settings
        List<OptionItem> commentsMax = new ArrayList<>();
        int curMaxComments = data.getCommentsMaxCount();
        commentsMax.add(UiOptionItem.from("10", opt -> data.setCommentsMaxCount(10), curMaxComments == 10));
        commentsMax.add(UiOptionItem.from("25", opt -> data.setCommentsMaxCount(25), curMaxComments == 25));
        commentsMax.add(UiOptionItem.from("50 (default)", opt -> data.setCommentsMaxCount(50), curMaxComments == 50));
        commentsMax.add(UiOptionItem.from("100", opt -> data.setCommentsMaxCount(100), curMaxComments == 100));
        dlg.appendRadioCategory("Max comments to analyze", commentsMax);

        List<OptionItem> commentsSource = new ArrayList<>();
        String curSource = data.getCommentsSource();
        commentsSource.add(UiOptionItem.from("Top comments", opt -> data.setCommentsSource("top"), "top".equalsIgnoreCase(curSource)));
        // Future: add "Recent comments"
        dlg.appendRadioCategory("Comments source", commentsSource);

        // Layout
        List<OptionItem> layout = new ArrayList<>();
        layout.add(UiOptionItem.from("Compact layout (smaller text, tighter spacing)", opt -> data.setCompactLayout(opt.isSelected()), data.isCompactLayout()));
        dlg.appendCheckedCategory("Layout", layout);

        // Verbose logging
        List<OptionItem> debug = new ArrayList<>();
        debug.add(UiOptionItem.from("Verbose logging", opt -> data.setDebugLogging(opt.isSelected()), data.isDebugLogging()));
        dlg.appendCheckedCategory("Debug", debug);

        // Email settings
        List<OptionItem> email = new ArrayList<>();
        String currentEmail = data.getSummaryEmail();
        email.add(UiOptionItem.from(
                "Set summary email" + (currentEmail != null && !currentEmail.isEmpty() ? " (" + currentEmail + ")" : ""),
                opt -> showEmailInputDialog(context, currentEmail),
                false));
        if (currentEmail != null && !currentEmail.isEmpty()) {
            email.add(UiOptionItem.from("Clear summary email", opt -> data.setSummaryEmail(null), false));
        }
        dlg.appendStringsCategory("Email", email);

        dlg.showDialog(context.getString(R.string.gemini_settings_title), null);
    }

    private void showOpenAICustomModelDialog(Context ctx, String current) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(ctx);
        android.view.View view = inflater.inflate(com.liskovsoft.smartyoutubetv2.common.R.layout.simple_edit_dialog, null);
        android.widget.EditText edit = view.findViewById(com.liskovsoft.smartyoutubetv2.common.R.id.simple_edit_value);
        edit.setHint("e.g., gpt-4o or gpt-4o-mini");
        if (current != null) edit.setText(current);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("Custom OpenAI model id")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String value = edit.getText() != null ? edit.getText().toString().trim() : null;
                    com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(ctx).setOpenAICustomModel((value != null && !value.isEmpty()) ? value : null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEmailInputDialog(Context ctx, String current) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(ctx);
        android.view.View view = inflater.inflate(com.liskovsoft.smartyoutubetv2.common.R.layout.simple_edit_dialog, null);
        android.widget.EditText edit = view.findViewById(com.liskovsoft.smartyoutubetv2.common.R.id.simple_edit_value);
        edit.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        edit.setHint("email@example.com");
        if (current != null) edit.setText(current);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("Summary email address")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String value = edit.getText() != null ? edit.getText().toString().trim() : null;
                    if (value != null && !value.isEmpty()) {
                        data.setSummaryEmail(value);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

