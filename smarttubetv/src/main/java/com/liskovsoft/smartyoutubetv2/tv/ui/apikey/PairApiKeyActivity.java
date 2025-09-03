package com.liskovsoft.smartyoutubetv2.tv.ui.apikey;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class PairApiKeyActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, new PairApiKeyFragment(), android.R.id.content);
        }
    }
}
