package com.miui.player.ui.controller;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.miui.player.R;
import com.miui.player.meta.Audio;
import com.miui.player.meta.MetaManager;
import com.miui.player.network.DownloadInfoManager;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.ValueCallback;
import com.miui.player.plugin.onlinepay.VipOrderHelper;
import com.miui.player.plugin.onlinepay.VipOrderHelper.VipOrderCallback;
import com.miui.player.ui.UIHelper;
import com.miui.player.util.AccountUtils;
import com.miui.player.util.MusicLog;
import com.miui.player.util.StorageConfig;
import com.miui.player.util.Utils;

import java.util.Arrays;

public class QualityAlert {

    static final String TAG = "QualityAlert";

    public static interface OnQualitySelectedListener {
        public void onSelected(int quality, boolean allow);
    }

    public static void show(final Activity a, boolean downloadTask, final int defaultQuality, final Audio audio,
                            final OnQualitySelectedListener r) {
        final CharSequence[] names = a.getResources().getTextArray(R.array.music_quality);
        final int[] states = new int[names.length];
        Arrays.fill(states, DownloadInfoManager.STATUS_DOWNLOAD_NONE);
        if (audio != null) {
            final String[] paths = MetaManager.getAllSortedDownloadedMP3Names();
            final String onlineId = audio.getId();
            final String title = audio.getTitle();
            final String artist = audio.getArtistName();
            for (int i = 0; i < names.length; ++i) {
                states[i] = DownloadInfoManager.getDownloadStatus(a, onlineId, title, artist, i, paths);
            }
        }

        final AlertDialog dialog;
        final QualityAdapter adapter = new QualityAdapter(names, states);
        final QualityAlertListener listener = new QualityAlertListener(a, r, states);
        final AlertDialog.Builder builder = new AlertDialog.Builder(a).setTitle(
                downloadTask ? R.string.choose_download_quality : R.string.choose_stream_quality);
        if (defaultQuality >= 0) {
            builder.setSingleChoiceItems(adapter, defaultQuality, listener);
            dialog = builder.create();
        } else {
            builder.setAdapter(adapter, null);
            dialog = builder.create();
            dialog.getListView().setOnItemClickListener(new OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    listener.onClick(dialog, position);
                }
            });
        }

        dialog.show();
    }

    static void doAfterRefreshVipPermission(final Activity activity, final OnQualitySelectedListener l,
                                            final int quality) {
        ValueCallback<Boolean> refreshListener = new ValueCallback<Boolean>() {

            @Override
            public void execute(Boolean isVip) {
                l.onSelected(quality, AccountPermissionHelper.allowMusic(quality));
            }
        };

        AccountPermissionHelper.refreshVipPermission(true,
                new VipOrderCallback(activity, Long.toString(VipOrderHelper.DEFAULT_PRODUCT_ID), refreshListener));
    }

    static class QualityAlertListener implements OnClickListener {
        final OnQualitySelectedListener mListener;
        final Activity mActivity;
        final int[] mStates;

        public QualityAlertListener(Activity a, OnQualitySelectedListener l, int[] states) {
            mListener = l;
            mActivity = a;
            mStates = states;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0 && mStates[which] != DownloadInfoManager.STATUS_DOWNLOAD_NONE) {
                return;
            }

            dialog.dismiss();
            if (mActivity.isFinishing() || which < 0) {
                mListener.onSelected(-1, false);
                return;
            }

            final int quality = which;
            if (AccountPermissionHelper.allowMusic(quality)) {
                mListener.onSelected(quality, true);
            } else {
                // UHD -> Login + VIP
                // HD  -> Login
                boolean hasLogin = AccountUtils.hasLoginXiaomiAccount();
                if (!hasLogin) {
                    AccountManagerCallback<Bundle> callback =
                            new AccountManagerCallback<Bundle>() {

                                @Override
                                public void run(AccountManagerFuture<Bundle> future) {
                                    if (mActivity.isFinishing() || future == null) {
                                        mListener.onSelected(quality, false);
                                        return;
                                    }

                                    if (quality == StorageConfig.QUALITY_UHD) {
                                        doAfterRefreshVipPermission(mActivity, mListener, quality);
                                    } else {
                                        mListener.onSelected(quality, AccountPermissionHelper.allowMusic(quality));
                                    }
                                }
                            };

                    UIHelper.toastSafe(R.string.login_first);
                    AccountUtils.loginXiaomiAccount(mActivity, callback);
                } else if (quality == StorageConfig.QUALITY_UHD) {
                    doAfterRefreshVipPermission(mActivity, mListener, quality);
                } else {
                    MusicLog.w(TAG, "showQualitiAltert warning! hasLogin=" + hasLogin + ", quality=" + quality);
                    mListener.onSelected(quality, false);
                }
            }
        }
    }

    static class QualityAdapter extends BaseAdapter {
        private final CharSequence[] mQulityNames;
        private int[] mStates;
        private LayoutInflater mInflater;

        QualityAdapter(CharSequence[] names, int[] states) {
            mQulityNames = names;
            mStates = states;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mInflater == null) {
                mInflater = LayoutInflater.from(parent.getContext());
            }

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.quality_alert_singlechoice, parent, false);
            }

            TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
            TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);
            text1.setText(mQulityNames[position]);
            if (mStates[position] == DownloadInfoManager.STATUS_DOWNLOAD_NONE) {
                text1.setEnabled(true);
                text2.setVisibility(View.GONE);
            } else {
                text1.setEnabled(false);
                text2.setVisibility(View.VISIBLE);
            }

            ImageView icon = (ImageView) convertView.findViewById(R.id.vip_icon);
            icon.setVisibility(position == StorageConfig.QUALITY_UHD ? View.VISIBLE : View.GONE);
            return convertView;
        }

        @Override
        public int getCount() {
            return mQulityNames.length;
        }

        @Override
        public Object getItem(int position) {
            return mQulityNames[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
