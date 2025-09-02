package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.StreamReminderService;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoMenuPresenter extends BaseMenuPresenter {
    private static final String TAG = VideoMenuPresenter.class.getSimpleName();
    private final MediaItemService mMediaItemService;
    private final AppDialogPresenter mDialogPresenter;
    private final MediaServiceManager mServiceManager;
    private Disposable mAddToPlaylistAction;
    private Disposable mNotInterestedAction;
    private Disposable mSubscribeAction;
    private Disposable mPlaylistsInfoAction;
    private Video mVideo;
    public static WeakReference<Video> sVideoHolder = new WeakReference<>(null);
    private boolean mIsNotInterestedButtonEnabled;
    private boolean mIsNotRecommendChannelEnabled;
    private boolean mIsRemoveFromHistoryButtonEnabled;
    private boolean mIsRemoveFromSubscriptionsButtonEnabled;
    private boolean mIsOpenChannelButtonEnabled;
    private boolean mIsOpenChannelUploadsButtonEnabled;
    private boolean mIsSubscribeButtonEnabled;
    private boolean mIsShareLinkButtonEnabled;
    private boolean mIsShareQRLinkButtonEnabled;
    private boolean mIsShareEmbedLinkButtonEnabled;
    private boolean mIsAddToPlaylistButtonEnabled;
    private boolean mIsAddToRecentPlaylistButtonEnabled;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsOpenPlaylistButtonEnabled;
    private boolean mIsAddToPlaybackQueueButtonEnabled;
    private boolean mIsPlayNextButtonEnabled;
    private boolean mIsShowPlaybackQueueButtonEnabled;
    private boolean mIsOpenDescriptionButtonEnabled;
    private boolean mIsOpenCommentsButtonEnabled;
    private boolean mIsPlayVideoButtonEnabled;
    private boolean mIsPlayVideoIncognitoButtonEnabled;
    private boolean mIsPlaylistOrderButtonEnabled;
    private boolean mIsStreamReminderButtonEnabled;
    private boolean mIsMarkAsWatchedButtonEnabled;
    private VideoMenuCallback mCallback;
    private List<PlaylistInfo> mPlaylistInfos;
    private final Map<Long, MenuAction> mMenuMapping = new HashMap<>();

    public interface VideoMenuCallback {
        int ACTION_UNDEFINED = 0;
        int ACTION_UNSUBSCRIBE = 1;
        int ACTION_REMOVE = 2;
        int ACTION_REMOVE_FROM_PLAYLIST = 3;
        int ACTION_REMOVE_FROM_QUEUE = 4;
        int ACTION_ADD_TO_QUEUE = 5;
        int ACTION_PLAY_NEXT = 6;
        int ACTION_REMOVE_AUTHOR = 7;
        void onItemAction(Video videoItem, int action);
    }

    public static class MenuAction {
        private final Runnable mAction;
        private final boolean mIsAuth;

        public MenuAction(Runnable action, boolean isAuth) {
            this.mAction = action;
            this.mIsAuth = isAuth;
        }

        public void run() {
            mAction.run();
        }

        public boolean isAuth() {
            return mIsAuth;
        }
    }

    private VideoMenuPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
        mServiceManager = MediaServiceManager.instance();
        mDialogPresenter = AppDialogPresenter.instance(context);

        initMenuMapping();
    }

    public static VideoMenuPresenter instance(Context context) {
        return new VideoMenuPresenter(context);
    }

    @Override
    protected Video getVideo() {
        return mVideo;
    }

    @Override
    protected AppDialogPresenter getDialogPresenter() {
        return mDialogPresenter;
    }

    @Override
    protected VideoMenuCallback getCallback() {
        return mCallback;
    }

    public void showMenu(Video video, VideoMenuCallback callback) {
        mCallback = callback;
        showMenu(video);
    }

    public void showMenu(Video video) {
        if (video == null) {
            return;
        }

        mVideo = video;
        sVideoHolder = new WeakReference<>(video);

        MediaServiceManager.instance().authCheck(this::bootstrapPrepareAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void bootstrapPrepareAndShowDialogSigned() {
        mPlaylistInfos = null;
        RxHelper.disposeActions(mPlaylistsInfoAction);
        if (isAddToRecentPlaylistButtonEnabled()) {
            mPlaylistsInfoAction = mMediaItemService.getPlaylistsInfoObserve(mVideo.videoId)
                    .subscribe(
                            videoPlaylistInfos -> {
                                mPlaylistInfos = videoPlaylistInfos;
                                prepareAndShowDialogSigned();
                            },
                            error -> Log.e(TAG, "Add to recent playlist error: %s", error.getMessage())
                    );
        } else {
            prepareAndShowDialogSigned();
        }
    }

    private void prepareAndShowDialogSigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.getTitle() : null;
            // No need to add author because: 1) This could be a channel card. 2) This info isn't so important.
            mDialogPresenter.showDialog(title);
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered()) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null && !menuAction.isAuth()) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mVideo != null ? mVideo.getTitle() : null;
            mDialogPresenter.showDialog(title);
        }
    }

    private void appendAddToPlaylistButton() {
        if (!mIsAddToPlaylistButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo() || mVideo.isPlaylistAsChannel()) {
            return;
        }

        getDialogPresenter().appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.dialog_add_to_playlist),
                        optionItem -> AppDialogUtil.showAddToPlaylistDialog(getContext(), mVideo, mCallback)
                ));
    }

    private void appendAddToRecentPlaylistButton() {
        if (!isAddToRecentPlaylistButtonEnabled()) {
            return;
        }

        String playlistId = GeneralData.instance(getContext()).getLastPlaylistId();
        String playlistTitle = GeneralData.instance(getContext()).getLastPlaylistTitle();

        if (playlistId == null || playlistTitle == null) {
            return;
        }

        appendSimpleAddToRecentPlaylistButton(playlistId, playlistTitle);
    }

    private boolean isAddToRecentPlaylistButtonEnabled() {
        return mIsAddToPlaylistButtonEnabled && mIsAddToRecentPlaylistButtonEnabled && mVideo != null && mVideo.hasVideo();
    }

    private void appendSimpleAddToRecentPlaylistButton(String playlistId, String playlistTitle) {
        if (mPlaylistInfos == null) {
            return;
        }

        boolean isSelected = false;
        for (PlaylistInfo playlistInfo : mPlaylistInfos) {
            if (playlistInfo.getPlaylistId().equals(playlistId)) {
                isSelected = playlistInfo.isSelected();
                break;
            }
        }
        boolean add = !isSelected;
        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        add ? R.string.dialog_add_to : R.string.dialog_remove_from, playlistTitle),
                        optionItem -> addRemoveFromPlaylist(playlistId, playlistTitle, add)
                )
        );
    }

    //private void appendReactiveAddToRecentPlaylistButton(String playlistId, String playlistTitle) {
    //    mDialogPresenter.appendSingleButton(
    //            UiOptionItem.from(getContext().getString(
    //                    R.string.dialog_add_remove_from, playlistTitle),
    //                    optionItem -> {
    //                        mPlaylistsInfoAction = mItemManager.getVideoPlaylistsInfoObserve(mVideo.videoId)
    //                                .subscribe(
    //                                        videoPlaylistInfos -> {
    //                                            for (VideoPlaylistInfo playlistInfo : videoPlaylistInfos) {
    //                                                if (playlistInfo.getPlaylistId().equals(playlistId)) {
    //                                                    addRemoveFromPlaylist(playlistInfo.getPlaylistId(), playlistInfo.getTitle(), !playlistInfo.isSelected());
    //                                                    break;
    //                                                }
    //                                            }
    //                                        },
    //                                        error -> {
    //                                            // Fallback to something on error
    //                                            Log.e(TAG, "Add to recent playlist error: %s", error.getMessage());
    //                                        }
    //                                );
    //                    }
    //            )
    //    );
    //}

    private void appendOpenChannelButton() {
        if (!mIsOpenChannelButtonEnabled) {
            return;
        }

        if (!ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        // Prepare to special type of channels that work as playlist
        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isPlaylistAsChannel() ? R.string.open_playlist : R.string.open_channel), optionItem -> {
                    MediaServiceManager.chooseChannelPresenter(getContext(), mVideo);
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendOpenPlaylistButton() {
        if (!mIsOpenPlaylistButtonEnabled) {
            return;
        }

        // Check view to allow open playlist in grid
        if (mVideo == null || !mVideo.hasPlaylist() || (mVideo.belongsToSamePlaylistGroup() && getViewManager().getTopView() == ChannelUploadsView.class)) {
            return;
        }

        // Prepare to special type of channels that work as playlist
        if (mVideo.isPlaylistAsChannel() && ChannelPresenter.canOpenChannel(mVideo)) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_playlist), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendOpenChannelUploadsButton() {
        if (!mIsOpenChannelUploadsButtonEnabled || mVideo == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_channel_uploads), optionItem -> ChannelUploadsPresenter.instance(getContext()).openChannel(mVideo)));
    }

    private void appendNotInterestedButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        if ((!mVideo.belongsToHome() && !mVideo.belongsToShorts()) || !mIsNotInterestedButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_interested), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendNotRecommendChannelButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken2() == null) {
            return;
        }

        if ((!mVideo.belongsToHome() && !mVideo.belongsToShorts()) || !mIsNotRecommendChannelEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.not_recommend_channel), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken2())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Mark as 'not interested' error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.you_wont_see_this_video);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendRemoveFromHistoryButton() {
        if (mVideo == null || !mVideo.belongsToHistory() || !mIsRemoveFromHistoryButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_history), optionItem -> {
                    if (mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
                        onRemoveFromHistoryDone();
                    } else {
                        mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                                .subscribe(
                                        var -> {},
                                        error -> Log.e(TAG, "Remove from history error: %s", error.getMessage()),
                                        this::onRemoveFromHistoryDone
                                );
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void onRemoveFromHistoryDone() {
        if (mCallback != null) {
            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.removed_from_history);
        }
        VideoStateService stateService = VideoStateService.instance(getContext());
        stateService.removeByVideoId(mVideo.videoId);
        stateService.persistState();
    }

    private void appendRemoveFromSubscriptionsButton() {
        if (mVideo == null || mVideo.mediaItem == null || mVideo.mediaItem.getFeedbackToken() == null) {
            return;
        }

        if (!mVideo.belongsToSubscriptions() || !mIsRemoveFromSubscriptionsButtonEnabled) {
            return;
        }

        RxHelper.disposeActions(mNotInterestedAction);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_subscriptions), optionItem -> {
                    mNotInterestedAction = mMediaItemService.markAsNotInterestedObserve(mVideo.mediaItem.getFeedbackToken())
                            .subscribe(
                                    var -> {},
                                    error -> Log.e(TAG, "Remove from subscriptions error: %s", error.getMessage()),
                                    () -> {
                                        if (mCallback != null) {
                                            mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                                        }
                                    }
                            );
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendRemoveFromNotificationsButton() {
        if (mVideo == null || mVideo.mediaItem == null) {
            return;
        }

        if (!mVideo.belongsToNotifications() || !mIsRemoveFromSubscriptionsButtonEnabled) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_from_subscriptions), optionItem -> {
                    MediaServiceManager.instance().hideNotification(mVideo);
                    if (mCallback != null) {
                        mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE);
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendMarkAsWatchedButton() {
        if (mVideo == null || !mVideo.hasVideo() || !mIsMarkAsWatchedButtonEnabled) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_as_watched), optionItem -> {
                    // Use video duration instead of 0 to mark as fully watched in history
                    long durationMs = mVideo.getDurationMs() > 0 ? mVideo.getDurationMs() : 1000;
                    MediaServiceManager.instance().updateHistory(mVideo, durationMs);
                    mVideo.markFullyViewed();
                    VideoStateService.instance(getContext()).save(new State(mVideo, durationMs));
                    Playlist.instance().sync(mVideo);
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendShareLinkButton() {
        if (!mIsShareLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendShareQRLinkButton() {
        if (!mIsShareQRLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareQRLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendShareEmbedLinkButton() {
        if (!mIsShareEmbedLinkButtonEnabled) {
            return;
        }

        AppDialogUtil.appendShareEmbedLinkDialogItem(getContext(), mDialogPresenter, mVideo);
    }

    private void appendOpenDescriptionButton() {
        if (!mIsOpenDescriptionButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.action_video_info),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            mServiceManager.loadMetadata(mVideo, metadata -> {
                                String description = metadata.getDescription();
                                if (description != null) {
                                    showLongTextDialog(description);
                                } else {
                                    mServiceManager.loadFormatInfo(mVideo, formatInfo -> {
                                        String newDescription = formatInfo.getDescription();
                                        if (newDescription != null) {
                                            showLongTextDialog(newDescription);
                                        } else {
                                            MessageHelpers.showMessage(getContext(), R.string.description_not_found);
                                        }
                                    });
                                }
                            });
                        }
                ));
    }

    private void appendOpenCommentsButton() {
        if (!mIsOpenCommentsButtonEnabled || mVideo == null) {
            return;
        }

        if (mVideo.videoId == null || mVideo.isLive || mVideo.isUpcoming) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.open_comments),
                        optionItem -> {
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
                            mServiceManager.loadMetadata(mVideo, metadata -> {
                                CommentsController controller = new CommentsController(getContext(), metadata);
                                controller.onButtonClicked(R.id.action_chat, PlayerUI.BUTTON_ON);
                            });
                        }
                ));
    }

    private void appendPlayVideoButton() {
        if (!mIsPlayVideoButtonEnabled || mVideo == null || mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.play_video),
                        optionItem -> {
                            PlaybackPresenter.instance(getContext()).openVideo(mVideo);
                            mDialogPresenter.closeDialog();
                        }
                ));
    }

    private void appendPlayVideoIncognitoButton() {
        if (!mIsPlayVideoIncognitoButtonEnabled || mVideo == null || mVideo.videoId == null) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.play_video_incognito),
                        optionItem -> {
                            mVideo.incognito = true;
                            PlaybackPresenter.instance(getContext()).openVideo(mVideo);
                            mDialogPresenter.closeDialog();
                        }
                ));
    }

    private void showLongTextDialog(String description) {
        mDialogPresenter.appendLongTextCategory(mVideo.getTitle(), UiOptionItem.from(description));
        mDialogPresenter.showDialog(mVideo.getTitle());
    }

    private void appendSubscribeButton() {
        if (!mIsSubscribeButtonEnabled) {
            return;
        }

        if (mVideo == null || mVideo.isPlaylistAsChannel() || (!mVideo.isChannel() && !mVideo.hasVideo())) {
            return;
        }

        mVideo.isSubscribed = mVideo.isSubscribed || mVideo.belongsToSubscriptions() || mVideo.belongsToChannelUploads();

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        mVideo.isSynced || mVideo.isSubscribed || (!mServiceManager.isSigned() && mVideo.channelId != null) ? mVideo.isSubscribed ?
                                R.string.unsubscribe_from_channel : R.string.subscribe_to_channel : R.string.subscribe_unsubscribe_from_channel),
                        optionItem -> toggleSubscribe()));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> getViewManager().startView(PlaybackView.class)
                )
        );
    }

    private void appendAddToPlaybackQueueButton() {
        if (!mIsAddToPlaybackQueueButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        Playlist playlist = Playlist.instance();
        // Toggle between add/remove while dialog is opened
        boolean containsVideo = playlist.containsAfterCurrent(mVideo);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(containsVideo ? R.string.remove_from_playback_queue : R.string.add_to_playback_queue),
                        optionItem -> {
                            if (containsVideo) {
                                playlist.remove(mVideo);
                                if (mCallback != null) {
                                    mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_QUEUE);
                                }
                            } else {
                                mVideo.fromQueue = true;
                                playlist.add(mVideo);
                                if (mCallback != null) {
                                    mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_ADD_TO_QUEUE);
                                }
                            }

                            closeDialog();
                            MessageHelpers.showMessage(getContext(), String.format("%s: %s",
                                    mVideo.getAuthor(),
                                    getContext().getString(containsVideo ? R.string.removed_from_playback_queue : R.string.added_to_playback_queue))
                            );
                        }));
    }

    private void appendPlayNextButton() {
        if (!mIsPlayNextButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        Playlist playlist = Playlist.instance();

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.play_next),
                        optionItem -> {
                            mVideo.fromQueue = true;
                            playlist.next(mVideo);
                            if (mCallback != null) {
                                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_PLAY_NEXT);
                            }

                            closeDialog();
                            MessageHelpers.showMessage(getContext(), String.format("%s: %s",
                                    mVideo.getAuthor(),
                                    getContext().getString(R.string.play_next))
                            );
                        }));
    }

    private void appendShowPlaybackQueueButton() {
        if (!mIsShowPlaybackQueueButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.hasVideo()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(
                        getContext().getString(R.string.action_playback_queue),
                        optionItem -> AppDialogUtil.showPlaybackQueueDialog(getContext(), video -> PlaybackPresenter.instance(getContext()).openVideo(video))
                )
        );
    }

    private void appendPlaylistOrderButton() {
        if (!mIsPlaylistOrderButtonEnabled) {
            return;
        }

        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (mVideo == null || !(presenter.isPlaylistsSection() && presenter.inForeground())) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(
                        R.string.playlist_order),
                        optionItem -> AppDialogUtil.showPlaylistOrderDialog(getContext(), mVideo, mDialogPresenter::closeDialog)
                ));
    }

    private void appendStreamReminderButton() {
        if (!mIsStreamReminderButtonEnabled) {
            return;
        }

        if (mVideo == null || !mVideo.isUpcoming) {
            return;
        }

        StreamReminderService reminderService = StreamReminderService.instance(getContext());
        boolean reminderSet = reminderService.isReminderSet(mVideo);

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(reminderSet ? R.string.unset_stream_reminder : R.string.set_stream_reminder),
                        optionItem -> {
                            reminderService.toggleReminder(mVideo);
                            closeDialog();
                            MessageHelpers.showMessage(getContext(), reminderSet ? R.string.msg_done : R.string.playback_starts_shortly);
                        }
                ));
    }

    private void addRemoveFromPlaylist(String playlistId, String playlistTitle, boolean add) {
        RxHelper.disposeActions(mAddToPlaylistAction);
        if (add) {
            Observable<Void> editObserve = mVideo.mediaItem != null ?
                    mMediaItemService.addToPlaylistObserve(playlistId, mVideo.mediaItem) : mMediaItemService.addToPlaylistObserve(playlistId, mVideo.videoId);
            // Handle error: Maximum playlist size exceeded (> 5000 items)
            mAddToPlaylistAction = RxHelper.execute(editObserve, error -> MessageHelpers.showLongMessage(getContext(), error.getMessage()));
            mDialogPresenter.closeDialog();
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.added_to, playlistTitle));
        } else {
            // Check that the current video belongs to the right section
            if (mCallback != null && Helpers.equals(mVideo.playlistId, playlistId)) {
                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
            }
            Observable<Void> editObserve = mMediaItemService.removeFromPlaylistObserve(playlistId, mVideo.videoId);
            mAddToPlaylistAction = RxHelper.execute(editObserve);
            mDialogPresenter.closeDialog();
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.removed_from, playlistTitle));
        }
    }

    //private void addRemoveFromPlaylist(String playlistId, String playlistTitle, boolean add) {
    //    if (add) {
    //        Observable<Void> editObserve = mItemManager.addToPlaylistObserve(playlistId, mVideo.videoId);
    //        mAddToPlaylistAction = RxUtils.execute(editObserve);
    //        mDialogPresenter.closeDialog();
    //        MessageHelpers.showMessage(getContext(),
    //                getContext().getString(R.string.added_to, playlistTitle));
    //    } else {
    //        AppDialogUtil.showConfirmationDialog(getContext(), () -> {
    //            // Check that the current video belongs to the right section
    //            if (mCallback != null && Helpers.equals(mVideo.playlistId, playlistId)) {
    //                mCallback.onItemAction(mVideo, VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST);
    //            }
    //            Observable<Void> editObserve = mItemManager.removeFromPlaylistObserve(playlistId, mVideo.videoId);
    //            mAddToPlaylistAction = RxUtils.execute(editObserve);
    //            mDialogPresenter.closeDialog();
    //            MessageHelpers.showMessage(getContext(),
    //                    getContext().getString(R.string.removed_from, playlistTitle));
    //        }, getContext().getString(R.string.dialog_remove_from, playlistTitle));
    //    }
    //}

    private void toggleSubscribe() {
        if (mVideo == null) {
            return;
        }

        //mVideo.isSynced = true; // default to subscribe

        // Until synced we won't really know weather we subscribed to a channel.
        // Exclusion: channel item (can't be synced)
        // Note, regular items (from subscribed section etc) aren't contain channel id
        if (mVideo.isSynced || mVideo.isSubscribed || mVideo.isChannel() || (!mServiceManager.isSigned() && mVideo.channelId != null)) {
            toggleSubscribe(mVideo);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

            mServiceManager.loadMetadata(mVideo, metadata -> {
                mVideo.sync(metadata);
                toggleSubscribe(mVideo);
            });
        }
    }

    private void toggleSubscribe(Video video) {
        if (video == null) {
            return;
        }

        RxHelper.disposeActions(mSubscribeAction);

        Observable<Void> observable = video.isSubscribed ?
                mMediaItemService.unsubscribeObserve(video.channelId) : mMediaItemService.subscribeObserve(video.channelId);

        mSubscribeAction = RxHelper.execute(observable);

        video.isSubscribed = !video.isSubscribed;

        if (!video.isSubscribed && mCallback != null) {
            mCallback.onItemAction(video, VideoMenuCallback.ACTION_UNSUBSCRIBE);
        }

        MessageHelpers.showMessage(getContext(), getContext().getString(!video.isSubscribed ? R.string.unsubscribed_from_channel : R.string.subscribed_to_channel));
    }

    @Override
    protected void updateEnabledMenuItems() {
        super.updateEnabledMenuItems();

        MainUIData mainUIData = MainUIData.instance(getContext());

        mIsOpenChannelUploadsButtonEnabled = true;
        mIsOpenPlaylistButtonEnabled = true;
        mIsReturnToBackgroundVideoEnabled = true;
        mIsOpenChannelButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_CHANNEL);
        mIsAddToRecentPlaylistButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_RECENT_PLAYLIST);
        mIsAddToPlaybackQueueButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_QUEUE);
        mIsPlayNextButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_NEXT);
        mIsAddToPlaylistButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST);
        mIsShareLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_LINK);
        mIsShareQRLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_QR_LINK);
        mIsShareEmbedLinkButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHARE_EMBED_LINK);
        mIsNotInterestedButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_NOT_INTERESTED);
        mIsNotRecommendChannelEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL);
        mIsRemoveFromHistoryButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY);
        mIsRemoveFromSubscriptionsButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS);
        mIsOpenDescriptionButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_DESCRIPTION);
        mIsPlayVideoButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_VIDEO);
        mIsPlayVideoIncognitoButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO);
        mIsSubscribeButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SUBSCRIBE);
        mIsStreamReminderButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_STREAM_REMINDER);
        mIsShowPlaybackQueueButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_SHOW_QUEUE);
        mIsPlaylistOrderButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_PLAYLIST_ORDER);
        mIsMarkAsWatchedButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_MARK_AS_WATCHED);
        mIsOpenCommentsButtonEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_OPEN_COMMENTS);
    }

    private void initMenuMapping() {
        mMenuMapping.clear();

        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_VIDEO, new MenuAction(this::appendPlayVideoButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, new MenuAction(this::appendPlayVideoIncognitoButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, new MenuAction(this::appendRemoveFromHistoryButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_STREAM_REMINDER, new MenuAction(this::appendStreamReminderButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, new MenuAction(this::appendAddToRecentPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, new MenuAction(this::appendAddToPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, new MenuAction(this::appendCreatePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, new MenuAction(this::appendRenamePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, new MenuAction(this::appendAddToNewPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_NOT_INTERESTED, new MenuAction(this::appendNotInterestedButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, new MenuAction(this::appendNotRecommendChannelButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, new MenuAction(() -> { appendRemoveFromSubscriptionsButton(); appendRemoveFromNotificationsButton(); }, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, new MenuAction(this::appendMarkAsWatchedButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, new MenuAction(this::appendPlaylistOrderButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, new MenuAction(this::appendAddToPlaybackQueueButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PLAY_NEXT, new MenuAction(this::appendPlayNextButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHOW_QUEUE, new MenuAction(this::appendShowPlaybackQueueButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, new MenuAction(this::appendOpenChannelButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, new MenuAction(this::appendOpenPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SUBSCRIBE, new MenuAction(this::appendSubscribeButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, new MenuAction(this::appendToggleExcludeFromContentBlockButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, new MenuAction(this::appendTogglePinVideoToSidebarButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, new MenuAction(this::appendSaveRemovePlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, new MenuAction(this::appendOpenDescriptionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_LINK, new MenuAction(this::appendShareLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, new MenuAction(this::appendShareQRLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, new MenuAction(this::appendShareEmbedLinkButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, new MenuAction(this::appendAccountSelectionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, new MenuAction(this::appendToggleHistoryButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, new MenuAction(this::appendClearHistoryButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, new MenuAction(this::appendOpenCommentsButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_GEMINI_SUMMARY, new MenuAction(this::appendGeminiSummaryButtons, false));

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            if (provider.getMenuType() != ContextMenuProvider.MENU_TYPE_VIDEO) {
                continue;
            }
            mMenuMapping.put(provider.getId(), new MenuAction(() -> appendContextMenuItem(provider), false));
        }
    }

    private void appendContextMenuItem(ContextMenuProvider provider) {
        MainUIData mainUIData = MainUIData.instance(getContext());
        if (mainUIData.isMenuItemEnabled(provider.getId()) && provider.isEnabled(getVideo())) {
            mDialogPresenter.appendSingleButton(
                    UiOptionItem.from(getContext().getString(provider.getTitleResId()), optionItem -> provider.onClicked(getVideo(), getCallback()))
            );
        }
    }

    private void appendGeminiSummaryButtons() {
        MainUIData mainUIData = MainUIData.instance(getContext());
        
        if (mainUIData.isMenuItemEnabled(MainUIData.MENU_ITEM_GEMINI_SUMMARY)) {
            // Only the default option (uses settings)
            mDialogPresenter.appendSingleButton(
                    UiOptionItem.from("AI Summary", optionItem -> {
                        mDialogPresenter.closeDialog();
                        showGeminiSummary(); // Uses settings
                    })
            );
        }
    }

    private void showGeminiSummary() {
        // Get current settings for default option
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData geminiData = 
            com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(getContext());
        showGeminiSummary(geminiData.getMode(), geminiData.getDetailLevel());
    }
    
    private void showGeminiSummary(String mode, String detailLevel) {
        Video video = getVideo();
        android.util.Log.d("VideoMenuPresenter", "=== MANUAL GEMINI SUMMARY DEBUG ===");
        if (video != null) {
            android.util.Log.d("VideoMenuPresenter", "Menu Video Title: " + video.title);
            android.util.Log.d("VideoMenuPresenter", "Menu Video Author: " + video.author);
            android.util.Log.d("VideoMenuPresenter", "Menu Video ID: " + video.videoId);
            android.util.Log.d("VideoMenuPresenter", "Menu Video startTimeSeconds: " + video.startTimeSeconds);
        } else {
            android.util.Log.d("VideoMenuPresenter", "Menu Video is NULL");
        }
        android.util.Log.d("VideoMenuPresenter", "===================================");
        if (video == null) return;

        // Close the current dialog first
        mDialogPresenter.closeDialog();

        // Create the proper overlay (like in VideoGridFragment)
        if (getContext() instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) getContext();
            com.liskovsoft.smartyoutubetv2.common.ui.summary.VideoSummaryOverlay summaryOverlay = 
                new com.liskovsoft.smartyoutubetv2.common.ui.summary.VideoSummaryOverlay(activity);
            
            summaryOverlay.showLoading("Summarizing " + (video.title != null ? video.title : "video") + "...");
            // Auto-mark videos watched - no button needed
            summaryOverlay.setOnConfirmListener(null);

            // Run AI API call in background thread
            new Thread(() -> {
                try {
                    com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(getContext());
                    String provider = gd.getProvider();
                    com.liskovsoft.smartyoutubetv2.common.misc.AIClient ai;
                    if ("openai".equalsIgnoreCase(provider)) {
                        ai = new com.liskovsoft.smartyoutubetv2.common.misc.OpenAIClient(getContext());
                        if (!ai.isConfigured()) {
                            // Fallback to Gemini if OpenAI key not set
                            ai = new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext());
                        }
                    } else {
                        ai = new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext());
                    }
                    
                    String summary;
                    String title = "AI Summary";
                    
                    if (ai.isConfigured()) {
                        int startSec = Math.max(0, video.startTimeSeconds);
                        summary = ai.summarize(video.title, video.author, video.videoId, detailLevel, startSec, mode);
                        // Keep title simple - details moved to bottom of summary
                        title = "AI Summary";
                        
                        // Auto-mark video as watched when summary is generated successfully
                        try {
                            com.liskovsoft.smartyoutubetv2.common.app.models.data.Video v = video;
                            if (v != null && v.hasVideo()) {
                                // Use video duration instead of 0 to mark as fully watched in history
                                long durationMs = v.getDurationMs() > 0 ? v.getDurationMs() : 1000; // Default to 1 second if duration unknown
                                com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().updateHistory(v, durationMs);
                                v.markFullyViewed();
                                com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(getContext()).save(
                                        new com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State(v, durationMs)
                                );
                                com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(getContext()).persistState();
                                com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist.instance().sync(v);
                                android.util.Log.d("VideoMenuPresenter", "Video auto-marked as watched with duration " + durationMs + "ms: " + v.title);
                                
                                // Show toast notification on UI thread
                                activity.runOnUiThread(() -> {
                                    android.widget.Toast.makeText(getContext(), "✓ " + getContext().getString(com.liskovsoft.smartyoutubetv2.common.R.string.mark_as_watched), android.widget.Toast.LENGTH_LONG).show();
                                });
                            }
                        } catch (Throwable e) {
                            android.util.Log.e("VideoMenuPresenter", "Error auto-marking video as watched: " + e.getMessage());
                        }
                    } else {
                        summary = "AI provider not configured. Add API key to assets (openai.properties or gemini.properties).";
                    }
                    
                    final String finalSummary = summary;
                    final String finalTitle = title;
                    
                    // Show the summary on the UI thread using the proper overlay
                    activity.runOnUiThread(() -> {
                        String formatted = beautifySummaryText(finalSummary);
                        CharSequence styled = styleSummary(formatted);
                        summaryOverlay.showText("🧠 " + finalTitle, styled);
                        
                        // Set up async fact checking and email functionality
                        setupSummaryOverlayActions(summaryOverlay, finalSummary, video, new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext()), activity);
                    });
                } catch (Exception e) {
                    String errorMsg = "Failed to get summary:\n" + e.getMessage();
                    activity.runOnUiThread(() -> {
                        summaryOverlay.showText("Error", errorMsg);
                    });
                }
            }).start();
        }
    }

    // Simple beautifier to enhance readability: convert ASCII bullets to dot bullets,
    // add a bit of spacing around section dividers, and normalize line breaks.
    private static String beautifySummaryText(String text) {
        if (text == null) return null;
        try {
            String out = text;
            // Convert leading hyphen bullets to •
            out = out.replaceAll("(?m)^-\\s+", "• ");
            // Normalize three dashes divider to a nicer break
            out = out.replaceAll("(?m)^---$", "\n────────────────\n");
            // Ensure separators before well-known sections
            out = out.replaceFirst("(?m)^[💬] Comments Summary", "────────────────\n💬 Comments Summary");
            out = out.replaceFirst("(?m)^\\*\\*Fact Check Results:\\*\\*", "────────────────\n**Fact Check Results:**");
            // Compact excessive blank lines
            out = out.replaceAll("\n{3,}", "\n\n");
            return out.trim();
        } catch (Throwable t) {
            return text;
        }
    }

    // Style headings with accent color and bold; keep body readable.
    private static CharSequence styleSummary(String text) {
        if (text == null) return null;
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(text);
        int len = ssb.length();
        int start = 0;
        int accent = android.graphics.Color.parseColor("#86C5FF");
        while (start < len) {
            int lineEnd = android.text.TextUtils.indexOf(ssb, '\n', start);
            if (lineEnd < 0) lineEnd = len;
            // Detect headings
            CharSequence line = ssb.subSequence(start, lineEnd);
            String s = line.toString();
            boolean isHeading = s.startsWith("🧠") || s.startsWith("💬") || s.startsWith("🔍") || s.startsWith("Comments Summary") || s.startsWith("**Fact Check Results:**");
            if (isHeading) {
                ssb.setSpan(new android.text.style.ForegroundColorSpan(accent), start, lineEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, lineEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new android.text.style.RelativeSizeSpan(1.06f), start, lineEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = lineEnd + 1;
        }
        return ssb;
    }

    /**
     * Set up email and fact check functionality for the summary overlay.
     * This implements the new async two-stage process: summary first, then fact check on demand.
     */
    private void setupSummaryOverlayActions(com.liskovsoft.smartyoutubetv2.common.ui.summary.VideoSummaryOverlay summaryOverlay, 
                                          String summary, 
                                          com.liskovsoft.smartyoutubetv2.common.app.models.data.Video video,
                                          com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient gemini,
                                          android.app.Activity activity) {
        com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData gd = 
            com.liskovsoft.smartyoutubetv2.common.prefs.GeminiData.instance(getContext());
        
        // Content segments accumulator to preserve ordering: Main → Comments → Fact check
        final java.util.concurrent.atomic.AtomicReference<String> commentsRef = new java.util.concurrent.atomic.AtomicReference<>(null);
        final java.util.concurrent.atomic.AtomicReference<String> factRef = new java.util.concurrent.atomic.AtomicReference<>(null);

        // Set up email functionality (if enabled)
        if (gd.isEmailSummariesEnabled()) {
            summaryOverlay.setOnEmailListener(() -> {
                try {
                    String to = gd.getSummaryEmail();
                    if (to == null || to.isEmpty()) {
                        android.widget.Toast.makeText(getContext(), "Set summary email in Settings", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    String subject = "SmartTube Summary: " + (video.title != null ? video.title : "Video");
                    String link = video.videoId != null ? ("https://www.youtube.com/watch?v=" + video.videoId) : "";
                    StringBuilder body = new StringBuilder();
                    body.append("Title: ").append(video.title).append("\n");
                    body.append("Channel: ").append(video.author).append("\n");
                    if (!link.isEmpty()) body.append("Link: ").append(link).append("\n");
                    body.append("\nSummary:\n").append(summary);

                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
                    intent.setData(android.net.Uri.parse("mailto:"));
                    intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{to});
                    intent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
                    intent.putExtra(android.content.Intent.EXTRA_TEXT, body.toString());
                    try {
                        activity.startActivity(intent);
                    } catch (Throwable e) {
                        android.widget.Toast.makeText(getContext(), "No email app found", android.widget.Toast.LENGTH_LONG).show();
                    }
                } catch (Throwable e) {
                    android.util.Log.e("VideoMenuPresenter", "Email summary error: " + e.getMessage());
                }
            });
        } else {
            // Hide email button when disabled
            summaryOverlay.setOnEmailListener(null);
        }

        // Summarize comments (async) if enabled
        if (gd.isCommentsSummaryEnabled()) {
            new Thread(() -> {
                try {
                    // Fetch comments key from metadata (blocking)
                    com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata md = mMediaItemService.getMetadata(video.videoId);
                    String commentsKey = md != null ? md.getCommentsKey() : null;
                    if (commentsKey == null) return; // no comments

                    java.util.List<String> texts = new java.util.ArrayList<>();
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    io.reactivex.disposables.Disposable[] holder = new io.reactivex.disposables.Disposable[1];
                    holder[0] = getCommentsService().getCommentsObserve(commentsKey)
                            .subscribe(group -> {
                                try {
                                    if (group != null && group.getComments() != null) {
                                        int max = gd.getCommentsMaxCount();
                                        int count = 0;
                                        for (com.liskovsoft.mediaserviceinterfaces.data.CommentItem item : group.getComments()) {
                                            if (item == null || item.getMessage() == null) continue;
                                            texts.add(item.getMessage());
                                            count++;
                                            if (count >= max) break;
                                        }
                                    }
                                } finally {
                                    if (holder[0] != null) holder[0].dispose();
                                    latch.countDown();
                                }
                            }, err -> {
                                if (holder[0] != null) holder[0].dispose();
                                latch.countDown();
                            });
                    // Wait for first page (fetch can be slow on some videos/networks)
                    latch.await(20, java.util.concurrent.TimeUnit.SECONDS);

                    if (texts.isEmpty()) return;
                    android.util.Log.d("VideoMenuPresenter", "Comments collected for summary: " + texts.size());
                    com.liskovsoft.smartyoutubetv2.common.misc.AIClient aiComments;
                    if ("openai".equalsIgnoreCase(gd.getProvider())) {
                        aiComments = new com.liskovsoft.smartyoutubetv2.common.misc.OpenAIClient(getContext());
                        if (!aiComments.isConfigured()) aiComments = new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext());
                    } else {
                        aiComments = new com.liskovsoft.smartyoutubetv2.common.misc.GeminiClient(getContext());
                    }
                    String csum = aiComments.summarizeComments(video.title, video.author, video.videoId, texts, Math.min(texts.size(), gd.getCommentsMaxCount()));
                    if (csum == null || csum.isEmpty()) return;
                    commentsRef.set(csum);

                    activity.runOnUiThread(() -> {
                        StringBuilder content = new StringBuilder();
                        content.append(summary);
                        if (commentsRef.get() != null) {
                            content.append("\n\n").append("💬 Comments Summary\n").append(commentsRef.get());
                        }
                        if (factRef.get() != null) content.append("\n\n").append("🔍 ").append(factRef.get());
                        String formatted = beautifySummaryText(content.toString());
                        CharSequence styled = styleSummary(formatted);
                        summaryOverlay.showText("🧠 AI Summary", styled);
                    });
                } catch (Throwable t) {
                    android.util.Log.w("VideoMenuPresenter", "Comments summary failed: " + t.getMessage());
                }
            }).start();
        }

        // Set up fact check functionality (async, on-demand)
        android.util.Log.d("VideoMenuPresenter", "Checking fact check setting: " + gd.isFactCheckEnabled());
        if (gd.isFactCheckEnabled()) {
            android.util.Log.d("VideoMenuPresenter", "✓ Fact checking ENABLED - starting async fact check for: " + video.title);
            
            // For now, auto-trigger fact check after a 3-second delay (temporary)
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 3 second delay
                    android.util.Log.d("VideoMenuPresenter", "Starting async fact check after delay...");
                    
                    String factCheckResult;
                    if ("openai".equalsIgnoreCase(gd.getProvider())) {
                        com.liskovsoft.smartyoutubetv2.common.misc.OpenAIClient oai = new com.liskovsoft.smartyoutubetv2.common.misc.OpenAIClient(getContext());
                        if (oai.isConfigured()) factCheckResult = oai.factCheck(summary, video.title, video.author, video.videoId);
                        else factCheckResult = gemini.factCheck(summary, video.title, video.author, video.videoId);
                    } else {
                        factCheckResult = gemini.factCheck(summary, video.title, video.author, video.videoId);
                    }
                    
                    android.util.Log.d("VideoMenuPresenter", "Fact check result: " + (factCheckResult != null ? "SUCCESS (" + factCheckResult.length() + " chars)" : "NULL"));
                    
                    if (factCheckResult != null && !factCheckResult.isEmpty()) {
                        // Update overlay with fact check results
                        activity.runOnUiThread(() -> {
                            factRef.set(factCheckResult);
                            StringBuilder content = new StringBuilder();
                            content.append(summary);
                            if (commentsRef.get() != null) content.append("\n\n").append("💬 Comments Summary\n").append(commentsRef.get());
                            content.append("\n\n").append("🔍 ").append(factRef.get());
                            String formatted = beautifySummaryText(content.toString());
                            CharSequence styled = styleSummary(formatted);
                            summaryOverlay.showText("🧠 AI Summary", styled);
                            android.util.Log.d("VideoMenuPresenter", "Fact check completed and overlay updated");
                        });
                    } else {
                        android.util.Log.w("VideoMenuPresenter", "Fact check returned empty or null result");
                    }
                } catch (Exception e) {
                    android.util.Log.e("VideoMenuPresenter", "Async fact check failed: " + e.getMessage(), e);
                }
            }).start();
        } else {
            android.util.Log.w("VideoMenuPresenter", "✗ Fact checking DISABLED in settings - not starting fact check");
        }
    }
}
