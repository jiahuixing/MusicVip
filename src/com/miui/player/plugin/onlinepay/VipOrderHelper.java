package com.miui.player.plugin.onlinepay;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import com.miui.player.R;
import com.miui.player.plugin.onlinesync.TokenManager;
import com.miui.player.plugin.onlinesync.baidu.BaiduSdkManager;
import com.miui.player.ui.UIHelper;
import com.miui.player.ui.VipRecommendActivity;
import com.miui.player.ui.base.MusicApplication;
import com.miui.player.util.AccountUtils;
import com.miui.player.util.MusicLog;
import com.miui.player.util.PreferenceCache;
import com.miui.player.util.StorageConfig;
import com.miui.player.util.ThreadManager;

import miui.accounts.ExtraAccountManager;
import miui.net.ExtendedAuthToken;
import miui.net.PaymentManager;
import miui.net.PaymentManager.PaymentListener;
import miui.net.SecureRequest;
import miui.net.SimpleRequest.MapContent;
import miui.net.SimpleRequest.StringContent;
import miui.net.exception.AccessDeniedException;
import miui.net.exception.AuthenticationFailureException;
import miui.net.exception.CipherException;
import miui.net.exception.InvalidResponseException;
import miui.util.EasyMap;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VipOrderHelper {

    private static final String TAG = "VipOrderHelper";

    private static final String HOST_URL = "https://order.music.xiaomi.com/";
    private static final String CREATE_ORDER_URL = HOST_URL + "createOrder/";
    private static final String USER_BOUGHT_URL = HOST_URL + "user/bought?";
    private static final String PAYMENT_ID = "110";
    private static final String SERVICE_ID = "miuimusic";
    private static final int CODE_SUCCESS = 200;
    public static final long DEFAULT_PRODUCT_ID = 1000000000000000L;

    private static final String KEY_XIAOMI_ID = "xiaomiId";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_PRODUCT_ID = "productId";
    private static final String KEY_ERROR_CODE = "errcode";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_SERVICE_TOKEN = "serviceToken";
    private static final String KEY_DATA = "data";

    public static class VipOrderCallback implements ValueCallback<Boolean> {

        private final Activity mActivity;
        private final ValueCallback<Boolean> mCallback;

        public VipOrderCallback(Activity activity, String productId, ValueCallback<Boolean> callback) {
            mActivity = activity;
            mCallback = callback;
        }

        @Override
        public void execute(Boolean isVip) {
            if (!isVip) {
                // 增加购买介绍页面
                Intent intent = new Intent(mActivity, VipRecommendActivity.class);
                mActivity.startActivity(intent);
            } else if (mCallback != null) {
                mCallback.execute(isVip);
            }
        }
    }

    public static void createOrderAsync(final Activity activity, final String productId) {
        Account account = ExtraAccountManager.getXiaomiAccount(MusicApplication.getApplication());
        if (account == null) {
            loginAlert(activity);
            return;
        }

        ThreadManager.postNetworkRequest(new Runnable() {

            @Override
            public void run() {
                doCreateOrder(activity, productId);
            }
        });
    }

    private static void doCreateOrder(final Activity activity, String productId) {
        if (!orderValid()) {
            UIHelper.toastSafe(R.string.requesting);
            return;
        }

        MapContent result = null;
        boolean success = false;
        if (BaiduSdkManager.initSdkEnvironment()) {
            sLastPayment = SystemClock.elapsedRealtime();
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
            Account account = ExtraAccountManager.getXiaomiAccount(activity);
            nameValuePairs.add(new BasicNameValuePair(KEY_XIAOMI_ID, account.name));
            nameValuePairs.add(new BasicNameValuePair(KEY_ACCESS_TOKEN, TokenManager.getAccessToken()));
            nameValuePairs.add(new BasicNameValuePair(KEY_PRODUCT_ID, productId));
            result = doPostByPassport(activity, CREATE_ORDER_URL, nameValuePairs);
        }

        if (result != null) {
            MusicLog.d(TAG, "order info = " + result.toString());
            int errorCode = (Integer) result.getFromBody(KEY_ERROR_CODE);
            if (CODE_SUCCESS == errorCode) {
                success = true;
                String order = (String) result.getFromBody(KEY_DATA);
                if (order != null) {
                    PaymentManager.get(activity).payForOrder(activity, PAYMENT_ID, order, null, new PaymentListener() {

                        @Override
                        public void onSuccess(String paymentId, Bundle result) {
                            UIHelper.toastSafe(R.string.payment_success);
                            sLastPayment = SystemClock.elapsedRealtime();
                            //购买成功后，试听偏好默认设置成超高品质
                            upgradeUserChoice(StorageConfig.BIT_RATE_UHD);
                            AccountPermissionHelper.refreshVipPermission(true, null, AccountPermissionHelper.DELAY_TIME);
                            AccountPermissionHelper.sHasBoughtVip = true;
                        }

                        @Override
                        public void onFailed(String paymentId, int code, String message, Bundle result) {
                            sLastPayment = -1;
                            if (code != PaymentManager.ERROR_CODE_CANCELED) {
                                UIHelper.toastSafe(R.string.payment_fail);
                            }
                        }
                    });
                }
            }
        }

        if (!success) {
            sLastPayment = -1;
            UIHelper.toastSafe(R.string.request_fail);
        }
    }

    private static MapContent doPostByPassport(Activity activity, String url, List<NameValuePair> nameValuePairs) {
        try {

            final Context context = MusicApplication.getApplication();
            AccountManager accountManager = AccountManager.get(context);
            Account account = ExtraAccountManager.getXiaomiAccount(context);
            if (account != null && accountManager != null) {
                String token;
                token = accountManager
                        .getAuthToken(account, SERVICE_ID,
                                null, true, null, null)
                        .getResult()
                        .getString(AccountManager.KEY_AUTHTOKEN);

                ExtendedAuthToken serviceToken = ExtendedAuthToken.parse(token);
                if (serviceToken != null) {
                    EasyMap<String, String> cookies = new EasyMap<String, String>()
                            .easyPut(KEY_USER_ID, account.name)
                            .easyPut(KEY_SERVICE_TOKEN, serviceToken.authToken);
                    return SecureRequest.postAsMap(url, parseToMap(nameValuePairs), cookies, true, serviceToken.security);
                }
            }
        } catch (OperationCanceledException e) {
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (AccessDeniedException e) {
            e.printStackTrace();
        } catch (InvalidResponseException e) {
            e.printStackTrace();
        } catch (CipherException e) {
            e.printStackTrace();
        } catch (AuthenticationFailureException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Map<String, String> parseToMap(List<NameValuePair> nameValuePairs) {
        Map<String, String> map = new HashMap<String, String>();
        for (NameValuePair pair : nameValuePairs) {
            map.put(pair.getName(), pair.getValue());
        }
        return map;
    }

    private static void loginAlert(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.login_first)
                .setPositiveButton(R.string.login, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AccountUtils.loginXiaomiAccount(activity, null);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void upgradeUserChoice(int bitRate) {
        Context context = MusicApplication.getApplication();
        PreferenceCache.setPrefAsInteger(context, PreferenceCache.PREF_TRACK_BIT_RATE, bitRate);
    }

    private static final long TIME_OUT = 60 * 1000; //过期时间1分钟
    private static long sLastPayment = -1;

    private static boolean orderValid() {
        return sLastPayment == -1 || (SystemClock.elapsedRealtime() - sLastPayment >= TIME_OUT);
    }

    public static boolean doRefreshBoughtVip() {
        try {
            final Context context = MusicApplication.getApplication();
            AccountManager accountManager = AccountManager.get(context);
            Account account = ExtraAccountManager.getXiaomiAccount(context);
            if (account != null) {
                String token = accountManager
                        .getAuthToken(account, SERVICE_ID,
                                null, true, null, null)
                        .getResult()
                        .getString(AccountManager.KEY_AUTHTOKEN);
                ExtendedAuthToken serviceToken = ExtendedAuthToken.parse(token);

                StringContent content = null;
                if (serviceToken != null) {
                    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                    nameValuePairs.add(new BasicNameValuePair(KEY_XIAOMI_ID, account.name));
                    nameValuePairs.add(new BasicNameValuePair(KEY_ACCESS_TOKEN, TokenManager.getAccessToken()));

                    EasyMap<String, String> cookies = new EasyMap<String, String>()
                            .easyPut(KEY_USER_ID, account.name)
                            .easyPut(KEY_SERVICE_TOKEN, serviceToken.authToken);

                    content = SecureRequest.getAsString(USER_BOUGHT_URL, parseToMap(nameValuePairs),
                            cookies, true, serviceToken.security);
                }

                if (content != null) {
                    return Boolean.parseBoolean(content.getBody());
                }
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (OperationCanceledException e) {
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            e.printStackTrace();
        } catch (CipherException e) {
            e.printStackTrace();
        } catch (AccessDeniedException e) {
            e.printStackTrace();
        } catch (InvalidResponseException e) {
            e.printStackTrace();
        } catch (AuthenticationFailureException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return false;
    }
}
