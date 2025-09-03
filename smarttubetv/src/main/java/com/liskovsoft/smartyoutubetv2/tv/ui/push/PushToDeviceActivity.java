package com.liskovsoft.smartyoutubetv2.tv.ui.push;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class PushToDeviceActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, new PushToDeviceFragment(), android.R.id.content);
        }
    }

    @Override
    public void finish() {
        // Avoid global app-exit logic in LeanbackActivity
        finishReally();
    }
}
