package com.miui.player.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.VipOrderHelper;
import com.miui.player.plugin.onlinesync.baidu.BaiduSdkEnvironment;
import com.miui.player.util.StorageConfig;

import miui.accounts.ExtraAccountManager;

public class AccountBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "AccountBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        BaiduSdkEnvironment env = BaiduSdkEnvironment.getInstance();
        final String action = intent.getAction();
        if (ExtraAccountManager.LOGIN_ACCOUNTS_POST_CHANGED_ACTION.equals(action)) {
            switch (intent.getIntExtra(ExtraAccountManager.EXTRA_UPDATE_TYPE, -1)) {
                case ExtraAccountManager.TYPE_ADD:
                    env.init();
                    break;

                case ExtraAccountManager.TYPE_REMOVE:
                    env.clear();
                    break;
            }
        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            env.init();
            AccountPermissionHelper.refreshVipPermission();
        }
    }
}
