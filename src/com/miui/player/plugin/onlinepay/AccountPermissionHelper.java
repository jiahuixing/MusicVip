package com.miui.player.plugin.onlinepay;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.baidu.music.login.LoginManager;
import com.baidu.music.model.User;
import com.miui.player.R;
import com.miui.player.plugin.onlinesync.baidu.BaiduSdkEnvironment;
import com.miui.player.plugin.onlinesync.baidu.BaiduSdkEnvironment.EnvironmentListener;
import com.miui.player.ui.base.MusicApplication;
import com.miui.player.util.MusicLog;
import com.miui.player.util.PreferenceCache;
import com.miui.player.util.StorageConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AccountPermissionHelper {

    private static final String TAG = "AccountPermissionHelper";
    private static final int LEVEL_VIP = 1; // level=0,非VIP用户；level=1，普通VIP
    private static final long MILLISECOND_OF_ONE_DAY = 24 * 60 * 60 * 1000;
    private static final long TIME_REMIND = 7 * MILLISECOND_OF_ONE_DAY;
    private static final String INPUT_DATE_FORMATE = "yyyy-MM-dd hh:mm:ss";
    private static final String OUTPUT_DATE_FORMATE = "yyyy.MM.dd";

    private static boolean sIsBind; //帐号是否暗绑成功
    private static boolean sIsVip;
    private static boolean sVipInitialized;
    private static boolean sVipTimeOut;
    private static boolean sHasBoughtVip = false;
    private static int sPermission = StorageConfig.QUALITY_NORMAL;

    private static final Object BOUGHT_LOCK = new Object();
    private static final Object PERMISSION_LOCK = new Object();

    private static final int MSG_REFRESH_PERMISSION = 0;
    private static final int MSG_REFRESH_BOUGHT = 1;

    private static final HandlerThread sBgThread;
    private static final Handler sBgHandler;

    static {
        sBgThread = new HandlerThread("AccountPermissionHelperBgThread");
        sBgThread.start();
        sBgHandler = new Handler(sBgThread.getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_REFRESH_PERMISSION:
                        ValueCallback<Boolean> callback = null;
                        if (msg.obj instanceof ValueCallback<?>) {
                            callback = (ValueCallback<Boolean>) msg.obj;
                        }
                        doRefreshVipPermission(callback);
                        break;

                    case MSG_REFRESH_BOUGHT:
                        boolean hasBoughtVip = VipOrderHelper.doRefreshBoughtVip();
                        synchronized (BOUGHT_LOCK) {
                            sHasBoughtVip = hasBoughtVip;
                        }
                        MusicLog.d(TAG, "sHasBoughtVip=" + sHasBoughtVip);
                        break;

                    default:
                        break;
                }
            }
        };
    }

    private static List<AccountPermissionListener> sListeners = new ArrayList<AccountPermissionListener>();

    public static void addListener(AccountPermissionListener listener) {
        synchronized (sListeners) {
            sListeners.add(listener);
        }
    }

    public static boolean removeListener(AccountPermissionListener listener) {
        synchronized (sListeners) {
            return sListeners.remove(listener);
        }
    }

    private static void notifyPermissionChanged() {
        synchronized (sListeners) {
            for (final AccountPermissionListener l : sListeners) {
                executeInUIThread(new Runnable() {

                    @Override
                    public void run() {
                        l.onPermissionChanged(sPermission);
                    }
                });
            }
        }
    }

    private static void notifyPeriodChanged() {
        synchronized (sListeners) {
            for (final AccountPermissionListener l : sListeners) {
                executeInUIThread(new Runnable() {

                    @Override
                    public void run() {
                        l.onPeriodChanged(getVipStartDate(), getVipEndDate());
                    }
                });
            }
        }
    }

    public static interface AccountPermissionListener {
        /**
         * Called in UI thread
         */
        public void onPermissionChanged(int allowQuality);

        /**
         * Called in UI thread
         */
        public void onPeriodChanged(String startTime, String endTime);
    }

    public static boolean allowNormalMusic() {
        return true;
    }

    public static boolean allowHDMusic() {
        synchronized (PERMISSION_LOCK) {
            return sIsBind;
        }
    }

    public static boolean allowUHDMusic() {
        synchronized (PERMISSION_LOCK) {
            return sIsBind && sIsVip;
        }
    }

    public static boolean allowMusic(int quality) {
        switch (quality) {
            case StorageConfig.QUALITY_UHD:
                return allowUHDMusic();

            case StorageConfig.QUALITY_HD:
                return allowHDMusic();

            case StorageConfig.QUALITY_NORMAL:
                return allowNormalMusic();

            default:
                break;
        }
        return false;
    }

    public static void refreshVipPermission() {
        refreshVipPermission(false, null);
    }

    private static void refreshVipPermissionAsync(final ValueCallback<Boolean> callback) {
        if (!sBgHandler.hasMessages(MSG_REFRESH_PERMISSION)) {
            sBgHandler.sendMessage(sBgHandler.obtainMessage(MSG_REFRESH_PERMISSION, callback));
        }
    }

    /**
     * @param forceRefresh
     * @param callback     will be called in UI thread
     */
    public static void refreshVipPermission(boolean forceRefresh, final ValueCallback<Boolean> callback) {
        refreshVipPermission(forceRefresh, callback, 0);
    }

    /**
     * @param forceRefresh
     * @param callback     will be called in UI thread
     * @param delayTime
     */
    public static void refreshVipPermission(boolean forceRefresh, final ValueCallback<Boolean> callback,
                                            long delayTime) {
        if (!sVipInitialized || forceRefresh) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    refreshVipPermissionInternal(callback);
                }
            }, delayTime > 0 ? delayTime : 0);
        } else {
            executeInUIThread(callback);
        }
    }

    /**
     * should be called in UI thread
     *
     * @param callback
     */
    private static void refreshVipPermissionInternal(final ValueCallback<Boolean> callback) {
        if (BaiduSdkEnvironment.getInstance().getStatus() == BaiduSdkEnvironment.SUCCESS) {
            refreshVipPermissionAsync(callback);
        } else {
            BaiduSdkEnvironment.getInstance().init(new Runnable() {

                @Override
                public void run() {
                    if (BaiduSdkEnvironment.getInstance().getStatus() == BaiduSdkEnvironment.SUCCESS) {
                        refreshVipPermissionAsync(callback);
                    } else {
                        executeInUIThread(callback);
                    }
                }
            });
        }
    }

    private static void doRefreshVipPermission(final ValueCallback<Boolean> callback) {
        MusicLog.d(TAG, "Refresh vip permission");
        boolean isVip = false;
        final Context context = MusicApplication.getApplication();
        LoginManager manager = LoginManager.getInstance(context);
        User user = manager.getUserVipLevel(context);
        if (user != null) {
            final int errorCode = user.getErrorCode();
            MusicLog.d(TAG, "user.getErrorCode() = " + user.getErrorCode());
            MusicLog.d(TAG, "user.getErrorDescription() = " + user.getErrorDescription());
            if (errorCode == User.OK) {
                final int level = user.getLevel();
                isVip = (level == LEVEL_VIP);
                if (!TextUtils.isEmpty(user.getVipStartTime()) &&
                        !TextUtils.isEmpty(user.getVipEndTime())) {
                    refreshDate(user.getVipStartTime(), user.getVipEndTime());
                    sVipInitialized = true;
                }
                MusicLog.d(TAG, "vip level = " + level);
            }
        } else {
            MusicLog.d(TAG, "user is null");
        }
        setPermissionValue(true, isVip);
        if (isVip) {
            synchronized (BOUGHT_LOCK) {
                sHasBoughtVip = true;
            }
            PreferenceCache.setPrefAsBoolean(context, PreferenceCache.PREF_VIP_TIME_OUT, false);
        }
        resetPerssion();

        executeInUIThread(callback);
        MusicLog.d(TAG, "Refresh vip permission end: isVip=" + isVip);
    }

    /**
     * @param startTime
     * @param endTime
     * @return true if currentTime is after startTime and before endTime, otherwise false
     */
    private static void refreshDate(String startTime, String endTime) {
        MusicLog.d(TAG, "vip start time = " + startTime + ", vip end time = " + endTime);
        SimpleDateFormat inputDateFormate = new SimpleDateFormat(INPUT_DATE_FORMATE);
        Date startDate = null;
        Date endDate = null;
        try {
            startDate = inputDateFormate.parse(startTime);
            endDate = inputDateFormate.parse(endTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (startDate != null && endDate != null) {
            Date currentDate = new Date();
            sVipTimeOut = currentDate.after(endDate);
            Context context = MusicApplication.getApplication();
            PreferenceCache.setPrefAsString(context, PreferenceCache.PREF_VIP_START_TIME,
                    startTime);
            PreferenceCache.setPrefAsString(context, PreferenceCache.PREF_VIP_END_TIME,
                    endTime);
            final boolean isVip;
            synchronized (PERMISSION_LOCK) {
                isVip = sIsVip;
            }
            if (isVip && currentDate.after(startDate) && currentDate.before(endDate)
                    && (endDate.getTime() - currentDate.getTime() > TIME_REMIND)) {
                PreferenceCache.setPrefAsBoolean(context, PreferenceCache.PREF_VIP_REMINDED, false);
            }
            notifyPeriodChanged();
        }
    }

    public static int getPermission() {
        return sPermission;
    }

    private static void resetPerssion() {
        final int newPerssion;
        if (allowUHDMusic()) {
            newPerssion = StorageConfig.QUALITY_UHD;
        } else if (allowHDMusic()) {
            newPerssion = StorageConfig.QUALITY_HD;
        } else {
            newPerssion = StorageConfig.QUALITY_NORMAL;
        }

        if (sPermission != newPerssion) {
            sPermission = newPerssion;
            notifyPermissionChanged();
        }
    }

    public static EnvironmentListener getEnvironmentListener() {
        return sEnvListener;
    }

    private static EnvironmentListener sEnvListener = new EnvironmentListener() {

        @Override
        public void onEnvironmentChanged(int state) {
            switch (state) {
                case BaiduSdkEnvironment.SUCCESS:
                    refreshVipPermission(true, null);
                    refreshBoughtVip();
                    break;

                default:
                    clear();
                    break;
            }
            resetPerssion();
        }
    };

    public static final long DELAY_TIME = 10 * 1000;

    public static String getVipStartDate() {
        return getOutputDate(getInputDate(PreferenceCache.PREF_VIP_START_TIME));
    }

    public static String getVipEndDate() {
        return getOutputDate(getInputDate(PreferenceCache.PREF_VIP_END_TIME));
    }

    private static Date getInputDate(String prefKey) {
        String dateString = PreferenceCache.getPrefAsString(MusicApplication.getApplication(),
                prefKey);
        Date date = null;
        if (dateString != null) {
            SimpleDateFormat inputDateFormate = new SimpleDateFormat(INPUT_DATE_FORMATE);
            try {
                date = inputDateFormate.parse(dateString);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return date;
    }

    private static String getOutputDate(Date date) {
        String dateString = null;
        if (date != null) {
            SimpleDateFormat outputDateFormate = new SimpleDateFormat(OUTPUT_DATE_FORMATE);
            dateString = outputDateFormate.format(date);
        }
        return dateString;
    }

    public static boolean needRemind() {
        Date startDate = getInputDate(PreferenceCache.PREF_VIP_START_TIME);
        Date endDate = getInputDate(PreferenceCache.PREF_VIP_END_TIME);
        if (startDate != null && endDate != null) {
            Date currentDate = new Date();
            if (currentDate.after(startDate) && currentDate.before(endDate) &&
                    endDate.getTime() - currentDate.getTime() <= TIME_REMIND) {
                return true;
            }
        }
        return false;
    }

    public static String getVipRemindText() {
        int remainDays = getVipRemainDays();
        if (remainDays >= 0) {
            final Context context = MusicApplication.getApplication();
            return context.getResources().getQuantityString(R.plurals.Nvip_expired_remind,
                    remainDays, remainDays);
        }
        return null;
    }

    public static int getVipRemainDays() {
        if (needRemind()) {
            Date endDate = getInputDate(PreferenceCache.PREF_VIP_END_TIME);
            Date currentDate = new Date();
            return (int) Math.ceil(1.0 * (endDate.getTime() - currentDate.getTime())
                    / MILLISECOND_OF_ONE_DAY);
        }
        return -1;
    }

    public static boolean isVipTimeOut() {
        return sVipTimeOut;
    }

    public static boolean hasInitialized() {
        return sVipInitialized;
    }

    public static boolean hasBoughtVip() {
        final boolean isBind;
        synchronized (PERMISSION_LOCK) {
            isBind = sIsBind;
        }
        synchronized (BOUGHT_LOCK) {
            return isBind && sHasBoughtVip;
        }
    }

    public static void refreshBoughtVip() {
        synchronized (BOUGHT_LOCK) {
            if (sHasBoughtVip) {
                return;
            }
        }
        if (!sBgHandler.hasMessages(MSG_REFRESH_BOUGHT)) {
            sBgHandler.sendEmptyMessage(MSG_REFRESH_BOUGHT);
        }
    }

    private static void clear() {
        MusicLog.d(TAG, "clear");
        setPermissionValue(false, false);
        sVipInitialized = false;
        sVipTimeOut = false;
        synchronized (BOUGHT_LOCK) {
            sHasBoughtVip = false;
        }
        resetPerssion();
        final Context context = MusicApplication.getApplication();
        PreferenceCache.setPrefAsString(context, PreferenceCache.PREF_VIP_START_TIME, null);
        PreferenceCache.setPrefAsString(context, PreferenceCache.PREF_VIP_END_TIME, null);
        notifyPeriodChanged();
    }

    private static void executeInUIThread(Runnable run) {
        if (run != null) {
            Handler handler = new Handler(MusicApplication.getApplication().getMainLooper());
            handler.post(run);
        }
    }

    private static void executeInUIThread(final ValueCallback<Boolean> callback) {
        if (callback != null) {
            executeInUIThread(new Runnable() {

                @Override
                public void run() {
                    final boolean isVip;
                    synchronized (PERMISSION_LOCK) {
                        isVip = sIsVip;
                    }
                    callback.execute(isVip);
                }
            });
        }
    }

    private static void setPermissionValue(final boolean isBind, final boolean isVip) {
        MusicLog.d(TAG, "set sIsBind=" + isBind + ", sIsVip=" + isVip);
        synchronized (PERMISSION_LOCK) {
            sIsBind = isBind;
            sIsVip = isVip;
        }
    }
}
