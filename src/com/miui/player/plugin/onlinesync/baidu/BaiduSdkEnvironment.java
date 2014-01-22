package com.miui.player.plugin.onlinesync.baidu;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.HashSet;

public class BaiduSdkEnvironment {
    public static final String TAG = "BaiduSdkEnvironment";

    public static final int UNINIT = 0;
    public static final int SUCCESS = 1;
    public static final int FAILURE = 2;

    private static final int MSG_UNINIT = 0;
    private static final int MSG_SUCCESS = 1;
    private static final int MSG_FAILURE = 2;

    private static final int MSG_REQUEST_INIT = 0;
    private static final int MSG_REQUEST_CLEAR = 1;

    public interface EnvironmentListener {
        public void onEnvironmentChanged(int state);
    }

    private static BaiduSdkEnvironment sInstance;

    private int mStatus = UNINIT;
    private HashSet<EnvironmentListener> mListeners = new HashSet<EnvironmentListener>();
    private final Handler mUIHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UNINIT:
                    mStatus = UNINIT;
                    break;
                case MSG_SUCCESS:
                    mStatus = SUCCESS;
                    break;
                case MSG_FAILURE:
                    mStatus = FAILURE;
                    break;
            }
            if (msg.obj instanceof Runnable) {
                ((Runnable) msg.obj).run();
            }
            Log.d(TAG, "baidu sdk env state: " + msg.what + ", notifyListener...");
            notifyListeners();
        }
    };

    private final HandlerThread mBgThread;
    private final Handler mBgHandler;

    private BaiduSdkEnvironment() {
        mBgThread = new HandlerThread("BaiduSdkEnvironmentBackgroundThread");
        mBgThread.start();
        mBgHandler = new Handler(mBgThread.getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_REQUEST_INIT:
                        boolean result = BaiduSdkManager.initSdkEnvironment();
                        mUIHandler.obtainMessage(result ? SUCCESS : FAILURE, msg.obj).sendToTarget();
                        break;
                    case MSG_REQUEST_CLEAR:
                        BaiduSdkManager.clearSdkEnvironment();
                        mUIHandler.obtainMessage(UNINIT).sendToTarget();
                    default:
                        break;
                }
            }
        };
    }

    public synchronized static BaiduSdkEnvironment getInstance() {
        if (sInstance == null) {
            sInstance = new BaiduSdkEnvironment();
        }
        return sInstance;
    }

    public int getStatus() {
        return mStatus;
    }

    public void addListener(EnvironmentListener l) {
        synchronized (mListeners) {
            mListeners.add(l);
        }
    }

    public void removeListener(EnvironmentListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    private void notifyListeners() {
        synchronized (mListeners) {
            for (EnvironmentListener l : mListeners) {
                l.onEnvironmentChanged(mStatus);
            }
        }
    }

    /**
     * 只能在UI线程调用
     */
    public void init() {
        init(null);
    }

    /**
     * 只能在UI线程调用,callback也在UI线程被执行
     */
    public void init(Runnable callback) {
        if (mStatus == SUCCESS) {
            if (callback != null) {
                callback.run();
            }
            return;
        }

        mBgHandler.obtainMessage(MSG_REQUEST_INIT, callback).sendToTarget();
    }

    /**
     * 只能在UI线程调用
     */
    public void clear() {
        if (mStatus == UNINIT) {
            return;
        }

        mBgHandler.obtainMessage(MSG_REQUEST_CLEAR).sendToTarget();
    }
}