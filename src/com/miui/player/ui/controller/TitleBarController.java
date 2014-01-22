package com.miui.player.ui.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.miui.player.R;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper.AccountPermissionListener;
import com.miui.player.service.MediaPlaybackService;
import com.miui.player.service.ServiceHelper;
import com.miui.player.ui.UIHelper;
import com.miui.player.ui.base.MusicApplication;
import com.miui.player.ui.controller.QualityAlert.OnQualitySelectedListener;
import com.miui.player.util.Network;
import com.miui.player.util.PreferenceCache;
import com.miui.player.util.ServiceActions;
import com.miui.player.util.ServiceActions.In;
import com.miui.player.util.StorageConfig;

public class TitleBarController {

    private static final int TEXT_PRIMARY = android.R.id.primary;
    private static final int TEXT_SECONDARY = android.R.id.message;
    private static final int TEXT_TERTIARY = android.R.id.hint;
    private static final int IMAGE_SEPARATOR = miui.R.id.separator1;
    private static final int IMAGE_BACK = android.R.id.home;
    private static final int IMAGE_TOGGLE = android.R.id.toggle;
    private static final int IMAGE_ICON = android.R.id.icon;
    private static final int BIT_RATE_ICON_CONTAINER = R.id.bit_rate_icon_container;
    private static final int BIT_RATE_ICON = R.id.bit_rate_icon;

    private final TextView mPrimaryText;
    private final TextView mSecondaryText;
    private final TextView mTertiaryText;
    private final ImageView mSeparator;
    private final ImageView mBackImage;
    private final ImageView mToggleImage;
    private final ImageView mIconImage;
    private final Activity mActivity;
    private final TextView mBitRateIcon;
    private final View mBitRateContainer;
    private VipPermissionChangeListener mVipPermissionChangedListener;

    private class VipPermissionChangeListener implements AccountPermissionListener {
        @Override
        public void onPermissionChanged(int allowQuality) {
            refreshBitRateIcon();
        }

        @Override
        public void onPeriodChanged(String startTime, String endTime) {
        }
    }

    public static class BitRateListener implements OnClickListener {
        private Activity mActivity;
        private View mContainer;
        private TextView mIcon;

        public BitRateListener(Activity activity, View container, TextView icon) {
            mActivity = activity;
            mContainer = container;
            mIcon = icon;
        }

        @Override
        public void onClick(View v) {
            QualityAlert.show(mActivity, false, readUserChoice(), null,
                    new OnQualitySelectedListener() {
                        @Override
                        public void onSelected(final int quality, boolean allow) {
                            if (allow) {
                                if (quality == StorageConfig.QUALITY_UHD &&
                                        Network.isActiveNetworkMetered(mActivity)) {
                                    final AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                                    builder.setTitle(R.string.data_usage_warning)
                                            .setMessage(R.string.data_usage_warning_summary)
                                            .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {

                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    dialog.dismiss();
                                                    saveUserChoice(StorageConfig.getMusicBitRate(quality));
                                                    refreshBitRateIcon(mActivity, mContainer, mIcon, true);
                                                }
                                            })
                                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    dialog.dismiss();
                                                }
                                            })
                                            .show();
                                } else {
                                    saveUserChoice(StorageConfig.getMusicBitRate(quality));
                                    refreshBitRateIcon(mActivity, mContainer, mIcon, true);
                                }
                            }
                        }
                    });
        }
    }

    ;


    public TitleBarController(final Activity activity, final View container) {
        mActivity = activity;
        mPrimaryText = (TextView) container.findViewById(TEXT_PRIMARY);
        mSecondaryText = (TextView) container.findViewById(TEXT_SECONDARY);
        mTertiaryText = (TextView) container.findViewById(TEXT_TERTIARY);
        mSeparator = (ImageView) container.findViewById(IMAGE_SEPARATOR);
        mBackImage = (ImageView) container.findViewById(IMAGE_BACK);
        mBackImage.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                activity.finish();
            }
        });
        mToggleImage = (ImageView) container.findViewById(IMAGE_TOGGLE);
        mIconImage = (ImageView) container.findViewById(IMAGE_ICON);
        mBitRateIcon = (TextView) container.findViewById(BIT_RATE_ICON);
        mBitRateContainer = (View) container.findViewById(BIT_RATE_ICON_CONTAINER);
        if (mBitRateContainer != null) {
            mBitRateContainer.setOnClickListener(new BitRateListener(mActivity, mBitRateContainer, mBitRateIcon));
            mVipPermissionChangedListener = new VipPermissionChangeListener();
            AccountPermissionHelper.addListener(mVipPermissionChangedListener);
        }
    }

    public void setPrimaryText(int resId) {
        mPrimaryText.setText(resId);
    }

    public void setPrimaryText(CharSequence text) {
        mPrimaryText.setText(text);
    }

    public void setSecondaryText(int resId) {
        mSecondaryText.setText(resId);
        updateSeparator();
    }

    public void setSecondaryText(CharSequence text) {
        mSecondaryText.setText(text);
        updateSeparator();
    }

    public void setTertiaryText(int resId) {
        mTertiaryText.setText(resId);
        updateSeparator();
    }

    public void setTertiaryText(CharSequence text) {
        mTertiaryText.setText(text);
        updateSeparator();
    }

    public void setBackDrawable(Drawable background) {
        mBackImage.setBackground(background);
    }

    public void setBackDrawable(int resId) {
        mBackImage.setBackgroundResource(resId);
    }

    public void setToggle(Drawable background) {
        mToggleImage.setBackground(background);
    }

    public void setToggle(int resId) {
        mToggleImage.setBackgroundResource(resId);
    }

    public void setToggleListener(OnClickListener listener) {
        mToggleImage.setOnClickListener(listener);
    }

    public void setIcon(Drawable background) {
        mIconImage.setBackground(background);
    }

    public void setIcon(int resId) {
        mIconImage.setBackgroundResource(resId);
    }

    public CharSequence getSecondaryText() {
        return mSecondaryText.getText();
    }

    public CharSequence getTertiaryText() {
        return mTertiaryText.getText();
    }

    private void updateSeparator() {
        if (mSeparator == null) {
            return;
        }

        if (TextUtils.isEmpty(getSecondaryText()) || TextUtils.isEmpty(getTertiaryText())) {
            mSeparator.setVisibility(View.GONE);
        } else {
            mSeparator.setVisibility(View.VISIBLE);
        }
    }

    private static void saveUserChoice(int bitRate) {
        Context context = MusicApplication.getApplication();
        int oldBitRate = PreferenceCache.getPrefAsInteger(context, PreferenceCache.PREF_TRACK_BIT_RATE);
        if (oldBitRate != bitRate) {
            PreferenceCache.setPrefAsInteger(context, PreferenceCache.PREF_TRACK_BIT_RATE, bitRate);
            doCommand(ServiceActions.In.CMDREPLAY);
        }
    }

    private static int readUserChoice() {
        Context context = MusicApplication.getApplication();
        int bitRate = PreferenceCache
                .getPrefAsInteger(context, PreferenceCache.PREF_TRACK_BIT_RATE);
        int choice = StorageConfig.getUserChoice(bitRate);
        switch (choice) {
            case StorageConfig.QUALITY_UHD:
                if (AccountPermissionHelper.allowUHDMusic()) {
                    return StorageConfig.QUALITY_UHD;
                }
            case StorageConfig.QUALITY_HD:
                if (AccountPermissionHelper.allowHDMusic()) {
                    return StorageConfig.QUALITY_HD;
                }
            default:
                return StorageConfig.QUALITY_NORMAL;
        }
    }

    private void refreshBitRateIcon() {
        String onlineId = ServiceHelper.getCurrentOnlineId();
        boolean isVisible = onlineId != null;
        TitleBarController.refreshBitRateIcon(mActivity, mBitRateContainer, mBitRateIcon, isVisible);
    }

    public void refreshBitRateIcon(boolean isVisible) {
        refreshBitRateIcon(mActivity, mBitRateContainer, mBitRateIcon, isVisible);
    }

    public static void refreshBitRateIcon(Activity activity, View container, TextView icon,
                                          boolean isVisible) {
        if (container == null || icon == null) {
            return;
        }

        container.setVisibility(isVisible ? View.VISIBLE : View.GONE);

        if (!isVisible) {
            return;
        }

        int choice = readUserChoice();
        switch (choice) {
            case StorageConfig.QUALITY_UHD:
                icon.setText(null);
                icon.setBackgroundResource(R.drawable.vip_diamond);
                return;
            case StorageConfig.QUALITY_HD:
                icon.setText(R.string.track_bit_rate_hd);
                icon.setBackgroundResource(R.drawable.media_playback_activity_title_bar_bit_rate_icon_bg);
                return;
            case StorageConfig.QUALITY_NORMAL:
                icon.setText(R.string.track_bit_rate_standard);
                icon.setBackgroundResource(R.drawable.media_playback_activity_title_bar_bit_rate_icon_bg);
                return;
        }
    }

    public void showUserGuide() {
        if (mBitRateContainer == null || mBitRateContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        UIHelper.showUserGuide(mActivity, mBitRateIcon, 0, 0,
                PreferenceCache.PREF_PAY_SERVICE_GUIDE_LISTEN, R.string.pay_service_guide_listen);
    }

    public static void doCommand(String command) {
        Context context = MusicApplication.getApplication();
        Intent i = new Intent(context, MediaPlaybackService.class);
        i.setAction(In.SERVICECMD);
        i.putExtra(In.CMDNAME, command);
        context.startService(i);
    }

    public void destory() {
        if (mVipPermissionChangedListener != null) {
            AccountPermissionHelper.removeListener(mVipPermissionChangedListener);
        }
    }
}
