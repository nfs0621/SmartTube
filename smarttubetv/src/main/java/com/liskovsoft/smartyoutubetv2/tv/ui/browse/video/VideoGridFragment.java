package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomVerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.summary.VideoSummaryOverlay;
import com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient;

import java.util.ArrayList;
import java.util.List;

public class VideoGridFragment extends GridFragment implements VideoSection {
    private static final String TAG = VideoGridFragment.class.getSimpleName();
    private static final int RESTORE_MAX_SIZE = 10_000;
    private VideoGroupObjectAdapter mGridAdapter;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private UriBackgroundManager mBackgroundManager;
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private int mSelectedItemIndex = -1;
    private Video mSelectedItem;
    private float mVideoGridScale;
    private final Runnable mRestoreTask = this::restorePosition;
    private final Handler mSummaryHandler = new Handler();
    private Runnable mSummaryRunnable;
    private Video mPendingSummaryVideo;
    private VideoSummaryOverlay mSummaryOverlay;
    private GeminiClient mGemini;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = getMainPresenter();
        mCardPresenter = isShorts() ? new ShortsCardPresenter() : new VideoCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();
        mVideoGridScale = MainUIData.instance(getActivity()).getVideoGridScale();
        mSummaryOverlay = new VideoSummaryOverlay(getActivity());
        mGemini = new GeminiClient(getContext());

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cancel any pending summary operations
        if (mSummaryHandler != null && mSummaryRunnable != null) {
            android.util.Log.d(TAG, "Cancelling pending Gemini summary on fragment destroy");
            mSummaryHandler.removeCallbacks(mSummaryRunnable);
        }
    }

    protected VideoGroupPresenter getMainPresenter() {
        return BrowsePresenter.instance(getContext());
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    private void applyPendingUpdates() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates);

        mPendingUpdates.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new CustomVerticalGridPresenter();
        presenter.setNumberOfColumns(
                GridFragmentHelper.getMaxColsNum(getContext(), isShorts() ? R.dimen.shorts_card_width : R.dimen.card_width, mVideoGridScale)
        );
        setGridPresenter(presenter);

        if (mGridAdapter == null) {
            mGridAdapter = new VideoGroupObjectAdapter(mCardPresenter);
            setAdapter(mGridAdapter);
        }
    }

    @Override
    public int getPosition() {
        return getSelectedPosition();
    }

    @Override
    public void setPosition(int index) {
        if (index < 0) {
            return;
        }

        mSelectedItemIndex = index;
        mSelectedItem = null;

        if (mGridAdapter != null && index < mGridAdapter.size()) {
            setSelectedPosition(index);
            mSelectedItemIndex = -1;
        }
    }

    @Override
    public void selectItem(Video item) {
        if (item == null) {
            return;
        }

        mSelectedItem = item;
        mSelectedItemIndex = -1;

        if (mGridAdapter != null) {
            int index = mGridAdapter.indexOfAlt(item);

            if (index != -1) {
                setSelectedPosition(index);
                mSelectedItem = null;
            }
        }
    }

    @Override
    public void update(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getBrowseGrid() != null && getBrowseGrid().isComputingLayout()) {
            return;
        }

        // Smooth remove animation
        if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            updateInt(group);
            return;
        }

        freeze(true);

        updateInt(group);

        freeze(false);
    }

    private void updateInt(VideoGroup group) {
        if (mGridAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            clear();
        } else if (action == VideoGroup.ACTION_REMOVE) {
            mGridAdapter.remove(group);
            return;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            mGridAdapter.removeAuthor(group);
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            mGridAdapter.sync(group);
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        mGridAdapter.add(group);

        restorePosition();
    }

    private void restorePosition() {
        LoadingManager.showLoading(getContext(), true); // Restore task takes some time

        setPosition(mSelectedItemIndex);
        selectItem(mSelectedItem);

        if ((mSelectedItemIndex == -1 && mSelectedItem == null) || mGridAdapter == null || mGridAdapter.size() > RESTORE_MAX_SIZE) {
            LoadingManager.showLoading(getContext(), false);
            return;
        }

        // Item not found? Lookup item in next group.
        if (mMainPresenter.hasPendingActions()) {
            TickleManager.instance().runTask(mRestoreTask, 500);
        } else {
            mMainPresenter.onScrollEnd((Video) mGridAdapter.get(mGridAdapter.size() - 1));
        }
    }

    /**
     * Disable scrolling on partially updated grid. This shouldn't fix card position bug on Android 4.4.
     */
    private void freeze(boolean freeze) {
        if (getBrowseGrid() != null) {
            getBrowseGrid().setScrollEnabled(!freeze);
            getBrowseGrid().setAnimateChildLayout(!freeze);
        }
    }

    @Override
    public void clear() {
        if (mGridAdapter != null) {
            // Fix: Invalid item position -1(-1). Item count:84 androidx.leanback.widget.VerticalGridView
            freeze(true);

            mGridAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mGridAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mGridAdapter.isEmpty();
    }

    protected boolean isShorts() {
        return false;
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item) {
            if (item instanceof Video) {
                mMainPresenter.onVideoItemLongClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewClickedListener implements androidx.leanback.widget.OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                mMainPresenter.onVideoItemClicked((Video) item);
            } else {
                Toast.makeText(getActivity(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            android.util.Log.d(TAG, "onItemSelected called with item: " + (item != null ? item.getClass().getSimpleName() : "null"));
            if (item instanceof Video) {
                Video video = (Video) item;
                android.util.Log.d(TAG, "Video selected: " + video.title + " (ID: " + video.videoId + ")");
                mBackgroundManager.setBackgroundFrom((Video) item);

                mMainPresenter.onVideoItemSelected((Video) item);

                checkScrollEnd((Video) item);

                scheduleSummary((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            int size = mGridAdapter.size();
            int index = mGridAdapter.indexOf(item);

            if (index > (size - (isShorts() ? ViewUtil.GRID_SCROLL_CONTINUE_NUM * 2 : ViewUtil.GRID_SCROLL_CONTINUE_NUM))) {
                mMainPresenter.onScrollEnd((Video) mGridAdapter.get(size - 1));
            }
        }
    }

    private void scheduleSummary(Video video) {
        // Cancel previous
        if (mSummaryRunnable != null) {
            android.util.Log.d(TAG, "Cancelling previous Gemini summary timer");
            mSummaryHandler.removeCallbacks(mSummaryRunnable);
            mSummaryRunnable = null;
        }
        
        // Check if video is null or same as previous
        if (video == null) {
            android.util.Log.d(TAG, "Not scheduling summary - video is null");
            return;
        }
        
        // Check settings
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(getContext());
        if (!gd.isEnabled()) {
            android.util.Log.d(TAG, "Gemini summaries disabled in settings");
            return;
        }
        
        // Don't reschedule for same video
        if (mPendingSummaryVideo != null && video.equals(mPendingSummaryVideo)) {
            android.util.Log.d(TAG, "Not rescheduling - same video already pending: " + video.title);
            return;
        }
        
        int delay = gd.getDelayMs();
        android.util.Log.d(TAG, "Scheduling Gemini summary for: " + video.title + " with delay: " + delay + "ms");
        mPendingSummaryVideo = video;
        final Video capturedVideo = video; // Capture the video to avoid race conditions
        mSummaryRunnable = () -> {
            android.util.Log.d(TAG, "Timer triggered - calling triggerSummary for: " + capturedVideo.title);
            triggerSummary(capturedVideo);
        };
        boolean posted = mSummaryHandler.postDelayed(mSummaryRunnable, Math.max(1000, delay));
        android.util.Log.d(TAG, "Timer posted successfully: " + posted);
    }

    private void triggerSummary(Video video) {
        android.util.Log.d(TAG, "triggerSummary called for: " + (video != null ? video.title : "null"));
        if (video == null || getActivity() == null) {
            android.util.Log.d(TAG, "triggerSummary aborted - video is null: " + (video == null) + ", activity is null: " + (getActivity() == null));
            return;
        }
        if (mSummaryOverlay == null) mSummaryOverlay = new VideoSummaryOverlay(getActivity());
        android.util.Log.d(TAG, "Showing Gemini loading overlay for: " + video.title);
        mSummaryOverlay.showLoading("Summarizing " + (video.title != null ? video.title : "video") + "...");

        new Thread(() -> {
            String body;
            String title = "Gemini Summary";
            try {
                android.util.Log.d(TAG, "Gemini API configured: " + (mGemini != null && mGemini.isConfigured()));
                if (mGemini != null && mGemini.isConfigured()) {
                    android.util.Log.d(TAG, "Calling Gemini API for video: " + video.title);
                    com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData geminiData = 
                        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(getContext());
                    String detailLevel = geminiData.getDetailLevel();
                    android.util.Log.d(TAG, "Using detail level: " + detailLevel);
                    body = mGemini.summarize(video.title, video.author, video.videoId, detailLevel);
                    android.util.Log.d(TAG, "Gemini API response received, length: " + body.length());
                } else {
                    body = "Gemini API key not configured.\n\nAdd it to assets/gemini.properties (API_KEY=...).";
                    android.util.Log.d(TAG, "Gemini API not configured");
                }
            } catch (Exception e) {
                body = "Failed to get summary:\n" + e.getMessage();
                android.util.Log.e(TAG, "Gemini API error: " + e.getMessage(), e);
            }
            final String fBody = body;
            final String fTitle = title;
            if (getActivity() == null) return;
            android.util.Log.d(TAG, "Updating UI with Gemini summary");
            getActivity().runOnUiThread(() -> mSummaryOverlay.showText(fTitle, fBody));
        }).start();
    }
}
