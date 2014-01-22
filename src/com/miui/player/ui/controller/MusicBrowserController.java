package com.miui.player.ui.controller;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.miui.player.R;
import com.miui.player.meta.AlbumManager;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper.AccountPermissionListener;
import com.miui.player.provider.MediaProviderHelper;
import com.miui.player.service.IMediaPlaybackService;
import com.miui.player.service.ServiceHelper;
import com.miui.player.ui.UIHelper;
import com.miui.player.ui.controller.TitleBarController.BitRateListener;
import com.miui.player.util.Actions;
import com.miui.player.util.MusicAnalyticsUtils;
import com.miui.player.util.PreferenceCache;

import miui.analytics.XiaomiAnalytics;
import miui.provider.ExtraSettings;
import miui.widget.MarqueeTextView;

public class MusicBrowserController implements OnClickListener {

    static final String TAG = "MusicBrowserController";

    final Activity mActivity;
    private final MarqueeTextView mPrimaryText;
    final AlbumBackground mAlbumBackground;
    private final ImageView mPlayButton;
    final ImageView mTabLight;
    private final ImageView mTopRoundCorner;
    private View mBitRateContainer;
    private TextView mBitRateIcon;
    private VipPermissionChangeListener mVipPermissionChangeListener;

    boolean mFirstVisible = true;
    Handler mHandler = new Handler();

    private class VipPermissionChangeListener implements AccountPermissionListener {
        @Override
        public void onPermissionChanged(int allowQuality) {
            refreshBitRateIcon();
        }

        @Override
        public void onPeriodChanged(String startTime, String endTime) {
        }
    }

    public MusicBrowserController(Activity a) {
        mActivity = a;

        View container = a.findViewById(R.id.action_bar_title);
        mPrimaryText = (MarqueeTextView) container.findViewById(R.id.primary_text);

        mAlbumBackground = AlbumBackground.createByType(container, AlbumBackground.TYPE_VIDEO_SMALL);

        final View mask = container.findViewById(R.id.album_shadow);
        mask.setOnClickListener(this);
        mPlayButton = (ImageView) container.findViewById(R.id.main_play_button);
        mPlayButton.setOnClickListener(this);
        mTabLight = (ImageView) container.findViewById(R.id.tab_light);
        mTopRoundCorner = (ImageView) container.findViewById(R.id.top_round_corner);

        mPlayButton.setVisibility(View.VISIBLE);
        container.findViewById(R.id.tab_indicator).setVisibility(View.VISIBLE);
        container.findViewById(R.id.album_shadow).setVisibility(View.VISIBLE);

        mBitRateContainer = container.findViewById(R.id.bit_rate_icon_container);
        mBitRateIcon = (TextView) container.findViewById(R.id.bit_rate_icon);
        mBitRateContainer.setOnClickListener(new BitRateListener(mActivity,
                mBitRateContainer, mBitRateIcon));
        mVipPermissionChangeListener = new VipPermissionChangeListener();
        AccountPermissionHelper.addListener(mVipPermissionChangeListener);
    }

    public void refreshTrackCount() {
        if (!ServiceHelper.isMusicLoaded()) {
            final int count = MediaProviderHelper.getTrackCount(mActivity, -1);
            final String hint = mActivity.getResources().getQuantityString(
                    R.plurals.Ntrack_in_sdcard_format, count, count);
            setSecondaryText(hint, null);
        }
    }

    public void onPause() {
        mAlbumBackground.onPause();

        mHandler.removeCallbacksAndMessages(null);
        if (mAlbumLoadTask != null) {
            mAlbumLoadTask.cancel(true);
            mAlbumLoadTask = null;
        }
    }

    public void onDestroy() {
        mAlbumBackground.onDestroy();
        AccountPermissionHelper.removeListener(mVipPermissionChangeListener);
    }

    public void onResume() {
        boolean isCutRoundCornerBySystem = Settings.System.getInt(mActivity.getContentResolver(),
                ExtraSettings.System.SHOW_ROUNDED_CORNERS,
                mActivity.getResources().getInteger(com.miui.internal.R.integer.config_show_rounded_corners_default))
                != 0;
        mTopRoundCorner.setVisibility(isCutRoundCornerBySystem ?
                View.GONE : View.VISIBLE);

        mAlbumBackground.onResume();
        refreshMediaInfo(true, ServiceHelper.getUpdateVersion());
    }

    private void refreshPlayingMeteInfo(boolean updateAlbum, int version) {
        IMediaPlaybackService service = ServiceHelper.sService;
        if (service == null) {
            return;
        }

        try {
            // 更新专辑封面
            String tr = service.getTrackName();
            String al = service.getAlbumName();
            String ar = service.getArtistName();

            mPrimaryText.setText(tr);
            setSecondaryText(al, ar);

            if (updateAlbum) {
                loadAlbum(al, ar, service.getAlbumId(), version);
            }
        } catch (RemoteException e) {
        }
        refreshBitRateIcon();
    }

    private void setSecondaryText(String first, String second) {
        final int visibility = TextUtils.isEmpty(first) || TextUtils.isEmpty(second) ? View.GONE : View.VISIBLE;
        mActivity.findViewById(R.id.secondary_text_separator).setVisibility(visibility);
        ((TextView) mActivity.findViewById(R.id.album_text)).setText(first);
        ((TextView) mActivity.findViewById(R.id.artist_text)).setText(second);
    }

    AsyncTask<Void, Void, Bitmap> mAlbumLoadTask = null;

    void loadAlbum(final String al, final String ar, final long albumId, final int updateVersion) {
        if (PreferenceCache.getPrefAsBoolean(mActivity, PreferenceCache.PREF_DISPLAY_ALBUM)) {
            mHandler.removeCallbacksAndMessages(null);
            if (mAlbumLoadTask != null) {
                mAlbumLoadTask.cancel(true);
                mAlbumLoadTask = null;
            }

            final AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {

                @Override
                protected Bitmap doInBackground(Void... params) {
                    return AlbumManager.getDisplayedAlbum(mActivity, albumId, al, ar, true,
                            mAlbumBackground.getWidth(), mAlbumBackground.getHeight(), false);
                }

                @Override
                protected void onPostExecute(Bitmap bm) {
                    mAlbumBackground.setBitmap(bm);
                    mTabLight.setVisibility(bm == null ? View.VISIBLE : View.GONE);
                    mAlbumLoadTask = null;
                    mUpdateVersion = updateVersion;
                }

            };

            if (mFirstVisible) {
                mFirstVisible = false;
                mHandler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        if (mAlbumLoadTask != null && mAlbumLoadTask.getStatus() == Status.FINISHED) {
                            Log.w(TAG, "bad task !");
                            return;
                        }

                        mAlbumLoadTask = task;
                        task.execute();
                    }

                }, 100);
            } else {
                mAlbumLoadTask = task;
                task.execute();
            }
        } else {
            mFirstVisible = false;
            mAlbumBackground.setBitmap(null);
            mTabLight.setVisibility(View.VISIBLE);
            mUpdateVersion = updateVersion;
        }
    }

    int mUpdateVersion = -1;

    public void refreshMediaInfo(boolean updateAlbum, int updateVersion) {
        if (ServiceHelper.sService == null) { // 等service连上会触发刷新
            return;
        }

        try {
            mPlayButton.setImageResource(ServiceHelper.sService.isPlaying() ?
                    R.drawable.main_button_pause : R.drawable.main_button_play);
        } catch (RemoteException e) {
        }

        if (mUpdateVersion == updateVersion) {
            return;
        }

        if (ServiceHelper.isMusicLoaded()) {
            refreshPlayingMeteInfo(updateAlbum, ServiceHelper.getUpdateVersion());
        } else {
            mPrimaryText.setText(R.string.click_to_shuffle);
            refreshTrackCount();
            mAlbumBackground.setDrawable(null);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mPlayButton) {
            XiaomiAnalytics analytics = XiaomiAnalytics.getInstance();
            analytics.trackEvent(MusicAnalyticsUtils.CLICK_HOMEPAGE_PLAY);
            if (ServiceHelper.sService != null) {
                try {
                    if (ServiceHelper.sService.isPlaying()) {
                        ServiceHelper.sService.pause();
                    } else {
                        ServiceHelper.sService.play();
                    }
                } catch (RemoteException e) {
                }
            }

            return;
        }

        if (ServiceHelper.isMusicLoaded()) {
            XiaomiAnalytics analytics = XiaomiAnalytics.getInstance();
            analytics.trackEvent(MusicAnalyticsUtils.CLICK_HOMEPAGE_ENTER_NOWPLAYING);
            Intent intent = new Intent(Actions.ACTION_PLAYBACK_VIEW)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mActivity.startActivity(intent);
        } else if (ServiceHelper.sService != null) {
            try {
                ServiceHelper.sService.play();
            } catch (RemoteException e) {
            }
        }
    }

    public void setAlbumBackgroundTranslationX(float translationX) {
        mAlbumBackground.setTranslationX(translationX);
    }

    public void setAlbumBackgroundTranslationY(float translationY) {
        mAlbumBackground.setTranslationY(translationY);
    }

    public boolean isVideoPlaying() {
        return mAlbumBackground.isVideoPlaying();
    }

    private void refreshBitRateIcon() {
        String onlineId = ServiceHelper.getCurrentOnlineId();
        boolean isVisible = onlineId != null;
        TitleBarController
                .refreshBitRateIcon(mActivity, mBitRateContainer, mBitRateIcon, isVisible);
        if (isVisible) {
            UIHelper.showUserGuide(mActivity, mBitRateIcon, 0, 0,
                    PreferenceCache.PREF_PAY_SERVICE_GUIDE_LISTEN,
                    R.string.pay_service_guide_listen);
        }
    }
}
