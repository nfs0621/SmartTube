package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class AISummaryAction extends Action {
    public AISummaryAction(Context context) {
        super(R.id.action_ai_summary);
        // Custom AI sparkle icon
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.action_ai_summary);
        setIcon(icon);
        setLabel1(context.getString(R.string.action_ai_summary));
    }
}
