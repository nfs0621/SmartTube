package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.RowPresenter.ViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter.ChannelHeaderCallback;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CustomListRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.VideoSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MultipleRowsFragment extends RowsSupportFragment implements VideoSection {
    private static final String TAG = MultipleRowsFragment.class.getSimpleName();
    private UriBackgroundManager mBackgroundManager;
    private ArrayObjectAdapter mRowsAdapter;
    private ListRowPresenter mRowPresenter;
    private Map<Integer, VideoGroupObjectAdapter> mVideoGroupAdapters;
    private final List<VideoGroup> mPendingUpdates = new ArrayList<>();
    private VideoGroupPresenter mMainPresenter;
    private VideoCardPresenter mCardPresenter;
    private ShortsCardPresenter mShortsPresenter;
    private int mSelectedRowIndex = -1;
    private ChannelHeaderCallback mChannelHeaderCallback;
    
    // Gemini auto-summary support
    private android.os.Handler mSummaryHandler;
    private Runnable mSummaryRunnable;
    private com.liskovsoft.smartyoutubetv2.common.app.models.data.Video mPendingSummaryVideo;
    private com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient mGemini;
    private com.liskovsoft.smartyoutubetv2.common.ui.summary.VideoSummaryOverlay mSummaryOverlay;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mMainPresenter = getMainPresenter();
        mCardPresenter = new VideoCardPresenter();
        mShortsPresenter = new ShortsCardPresenter();
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        // Initialize Gemini support
        mSummaryHandler = new android.os.Handler();
        mGemini = new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext());
        // Don't initialize overlay here - create only when needed to avoid persistent display

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();
    }

    protected void addHeader(ChannelHeaderCallback callback) {
        mChannelHeaderCallback = callback;
    }

    protected abstract VideoGroupPresenter getMainPresenter();

    private void applyPendingUpdates() {
        // prevent modification within update method
        List<VideoGroup> copyArray = new ArrayList<>(mPendingUpdates);

        mPendingUpdates.clear();

        for (VideoGroup group : copyArray) {
            update(group);
        }
    }

    private void setupAdapter() {
        if (mVideoGroupAdapters == null) {
            mVideoGroupAdapters = new HashMap<>();
        }

        if (mRowsAdapter == null) {
            mRowPresenter = new CustomListRowPresenter();

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            presenterSelector.addClassPresenter(ListRow.class, mRowPresenter);
            presenterSelector.addClassPresenter(ChannelHeaderCallback.class, new ChannelHeaderPresenter());

            mRowsAdapter = new ArrayObjectAdapter(presenterSelector);
            setAdapter(mRowsAdapter);
        }
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        mCardPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
        mShortsPresenter.setOnItemViewLongPressedListener(new ItemViewLongPressedListener());
    }

    @Override
    public void clear() {
        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
            if (mChannelHeaderCallback != null) {
                mRowsAdapter.add(mChannelHeaderCallback);
            }
        }

        if (mVideoGroupAdapters != null) {
            mVideoGroupAdapters.clear();
        }

        // Reset the position (bug appeared after fragment been reused)
        setPosition(mChannelHeaderCallback != null ? 1 : 0);
    }

    private void removeByIndex(int idx) {
        if (mRowsAdapter != null && mRowsAdapter.size() > idx) {
            ListRow row = (ListRow) mRowsAdapter.get(idx);
            mRowsAdapter.remove(row);
            VideoGroupObjectAdapter group = (VideoGroupObjectAdapter) row.getAdapter();
            mVideoGroupAdapters.values().remove(group);
        }
    }

    private void removeById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        mRowsAdapter.remove(row);
                        mVideoGroupAdapters.remove(id);
                    }
                }
            }
        }
    }

    private int findPositionById(int id) {
        if (mRowsAdapter != null) {
            VideoGroupObjectAdapter needed = mVideoGroupAdapters.get(id);
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                Object row = mRowsAdapter.get(i);

                if (row instanceof ListRow) {
                    VideoGroupObjectAdapter adapter = (VideoGroupObjectAdapter) ((ListRow) row).getAdapter();
                    if (adapter == needed) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private boolean isComputingLayout(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if ((action == VideoGroup.ACTION_SYNC || action == VideoGroup.ACTION_REPLACE) && getVerticalGridView() != null) {
            if (getVerticalGridView().isComputingLayout()) {
                return true;
            }
            int position = findPositionById(group.getId());
            if (position != -1) {
                RecyclerView.ViewHolder viewHolder = getVerticalGridView().findViewHolderForAdapterPosition(position);
                if (viewHolder != null) {
                    Object nestedRecyclerView = Helpers.getField(viewHolder, "mNestedRecyclerView");
                    if (nestedRecyclerView instanceof WeakReference) {
                        Object recyclerView = ((WeakReference<?>) nestedRecyclerView).get();
                        return recyclerView instanceof RecyclerView && ((RecyclerView) recyclerView).isComputingLayout();
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        if (mRowsAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mRowsAdapter.size() == 0;
    }

    @Override
    public void update(VideoGroup group) {
        if (isComputingLayout(group)) {
            return;
        }

        int action = group.getAction();

        // Smooth remove animation
        if (action == VideoGroup.ACTION_REMOVE) {
            updateInt(group);
            return;
        }

        freeze(true);

        updateInt(group);

        freeze(false);
    }

    private void updateInt(VideoGroup group) {
        if (mVideoGroupAdapters == null) {
            mPendingUpdates.add(group);
            return;
        }

        // Correct position depending on the search bar presence
        if (group.getPosition() != -1 && mChannelHeaderCallback != null) {
            group.setPosition(group.getPosition() + 1);
        }

        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) {
                clear();
            } else {
                removeById(group.getId());
            }
        } else if (action == VideoGroup.ACTION_REMOVE) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                adapter.remove(group);
            }
            return;
        } else if (action == VideoGroup.ACTION_SYNC) {
            VideoGroupObjectAdapter adapter = mVideoGroupAdapters.get(group.getId());
            if (adapter != null) {
                adapter.sync(group);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mVideoGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group, group.isShorts() ? mShortsPresenter : mCardPresenter);

            mVideoGroupAdapters.put(mediaGroupId, mediaGroupAdapter);

            ListRow row = new ListRow(rowHeader, mediaGroupAdapter);

            if (group.getPosition() == -1 || group.getPosition() > mRowsAdapter.size()) {
                mRowsAdapter.add(row);
            } else {
                mRowsAdapter.add(group.getPosition(), row);
            }
        } else {
            Log.d(TAG, "Continue row %s %s", group.getTitle(), System.currentTimeMillis());

            existingAdapter.add(group); // continue
        }

        restorePosition();
    }

    private void restorePosition() {
        setPosition(mSelectedRowIndex);

        // Maybe we don't need to load next group since all rows already fetched?
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

        if (mRowsAdapter != null && index < mRowsAdapter.size()) {
            setSelectedPosition(index, false);
            mSelectedRowIndex = -1;
        } else {
            mSelectedRowIndex = index;
        }
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }

    /**
     * Disable scrolling on partially updated rows. This prevent cards from misbehaving.
     */
    private void freeze(boolean freeze) {
        // Disable scrolling on partially updated rows. This prevent controls from misbehaving.
        if (mRowPresenter != null) {
            ViewHolder vh = getRowViewHolder(getSelectedPosition());
            if (vh instanceof ListRowPresenter.ViewHolder) {
                mRowPresenter.freeze(vh, freeze);
            }
        }
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
            if (item instanceof Video) {
                mBackgroundManager.setBackgroundFrom((Video) item);

                mMainPresenter.onVideoItemSelected((Video) item);

                checkScrollEnd((Video)item);
                
                // Auto-timer disabled temporarily - focus on manual summaries
                // scheduleSummary((Video) item);
            }
        }

        private void checkScrollEnd(Video item) {
            for (VideoGroupObjectAdapter adapter : mVideoGroupAdapters.values()) {
                int index = adapter.indexOf(item);

                if (index != -1) {
                    int size = adapter.size();
                    if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
                        mMainPresenter.onScrollEnd((Video) adapter.get(size - 1));
                    }
                    break;
                }
            }
        }
    }
    
    // Gemini auto-summary methods (copied from VideoGridFragment)
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
        mSummaryHandler.postDelayed(mSummaryRunnable, delay);
    }

    private void triggerSummary(Video video) {
        android.util.Log.d(TAG, "triggerSummary called for: " + (video != null ? video.title : "null"));
        if (video != null) {
            android.util.Log.d(TAG, "=== VIDEO DEBUG INFO ===");
            android.util.Log.d(TAG, "Video Title: " + video.title);
            android.util.Log.d(TAG, "Video Author: " + video.author);
            android.util.Log.d(TAG, "Video ID: " + video.videoId);
            android.util.Log.d(TAG, "Video startTimeSeconds: " + video.startTimeSeconds);
            android.util.Log.d(TAG, "========================");
        }
        if (video == null || getActivity() == null) {
            android.util.Log.d(TAG, "triggerSummary aborted - video is null: " + (video == null) + ", activity is null: " + (getActivity() == null));
            return;
        }

        mSummaryRunnable = null; // Clear the runnable since it's now executing
        mPendingSummaryVideo = null;

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
                    int startSec = Math.max(0, video.startTimeSeconds);
                    body = mGemini.summarize(video.title, video.author, video.videoId, detailLevel, startSec);
                    android.util.Log.d(TAG, "Gemini API response received, length: " + body.length());
                    // Update title to include model used
                    title = "Gemini Summary [" + mGemini.getLastUsedModel() + "]";
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
            getActivity().runOnUiThread(() -> {
                mSummaryOverlay.setOnConfirmListener(() -> {
                    try {
                        com.liskovsoft.smartyoutubetv2.common.app.models.data.Video v = video;
                        if (v != null && v.hasVideo()) {
                            // Show toast FIRST with longer duration and better visibility
                            android.widget.Toast.makeText(getContext(), "✓ " + getContext().getString(com.liskovsoft.smartyoutubetv2.common.R.string.mark_as_watched), android.widget.Toast.LENGTH_LONG).show();
                            
                            com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().updateHistory(v, 0);
                            v.markFullyViewed();
                            com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(getContext()).save(
                                    new com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State(v, v.getDurationMs())
                            );
                            com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(getContext()).persistState();
                            com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist.instance().sync(v);
                            android.util.Log.d(TAG, "Video marked as watched: " + v.title);
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "Error marking video as watched: " + e.getMessage());
                    }
                });
                mSummaryOverlay.showText(fTitle, fBody);
            });
        }).start();
    }
}
