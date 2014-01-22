package com.miui.player.plugin.onlinesync

nesync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;

import com.miui.player.ui.base.MusicApplication;
import com.miui.player.util.Network;
import com.miui.player.util.StreamHelper;

import miui.accounts.ExtraAccountManager;
import miui.net.ExtendedAuthToken;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

public class TokenManager {
    private static final String TAG = "TokenManager";

    private static final String HOST = "https://open.account.xiaomi.com/baidu/token?";
    private static final String APP_ID = "299409";
    private static final String THIRD_ID = "2882303761517117440";
    private static final String SID = "miuimusic";

    private static volatile String REFRESH_TOKEN;
    private static volatile String ACCESS_TOKEN;
    private static volatile long EXPIRES_IN;
    private static volatile long LAST_TIME_GET_TOKENS;

    private static final String KEY_TOKEN_SETTINGS = "token_settings";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_EXPIRES_IN = "expires_in";
    private static final String KEY_LAST_TIME_GET_TOKENS = "last_time_get_tokens";
    private static final String KEY_RESULT = "result";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_RESULT_CODE = "code";
    private static final String KEY_DATA = "data";

    private static final long DAY_OF_MILLISECONDS = 24 * 60 * 60 * 1000;

    private static String getUrl(String userId) {
        String url = String.format("%sappId=%s&thirdId=%s&userId=%s&sid=%s&devId=%s", HOST, APP_ID,
                THIRD_ID, userId, SID, getDeviceId());
        return url;
    }

    //耗时操作，请在非UI线程使用
    public static boolean requestTokensFromServer() {
        if (isRequestTokensSucceed()) {
            Log.d(TAG, "tokens already available.");
            return true;
        }

        if (requestTokensFromStorage()) {
            Log.d(TAG, "get tokens from storage.");
            return true;
        }

        Context context = MusicApplication.getApplication();
        Account account = ExtraAccountManager.getXiaomiAccount(context);
        if (account == null) {
            Log.e(TAG, "account is null.");
            return false;
        }
        ExtendedAuthToken extToken = getExtToken();
        if (extToken == null) {
            Log.e(TAG, "extToken is null.");
            return false;
        }
        Header cookie = getCookie(account, extToken);
        String url = getUrl(account.name);
        InputStream is = null;
        try {
            is = Network.doHttpGet(url, cookie);
            if (is == null) {
                Log.e(TAG, "InputStream is null.");
                return false;
            }

            final JSONObject json = StreamHelper.toJSONObject(is);
            if (json == null) {
                Log.e(TAG, "JSON is null.");
                return false;
            }

            final String result = json.optString(KEY_RESULT);
            final String description = json.optString(KEY_DESCRIPTION);
            final int code = json.optInt(KEY_RESULT_CODE);

            if (result == null || TextUtils.equals("error", result)) {
                Log.e(TAG, "result is incorrect, description:" + description + " code:" + code);
                return false;
            }

            final JSONObject data = json.optJSONObject(KEY_DATA);
            if (data == null) {
                Log.e(TAG, "data is null.");
                return false;
            }

            REFRESH_TOKEN = data.optString(KEY_REFRESH_TOKEN);
            ACCESS_TOKEN = data.optString(KEY_ACCESS_TOKEN);
            EXPIRES_IN = data.optLong(KEY_EXPIRES_IN) * 1000;
            LAST_TIME_GET_TOKENS = System.currentTimeMillis();
            putString(KEY_ACCESS_TOKEN, ACCESS_TOKEN);
            putString(KEY_REFRESH_TOKEN, REFRESH_TOKEN);
            putLong(KEY_EXPIRES_IN, EXPIRES_IN);
            putLong(KEY_LAST_TIME_GET_TOKENS, LAST_TIME_GET_TOKENS);
            Log.d(TAG, "access_token:" + ACCESS_TOKEN + "\nrefresh_token:"
                    + REFRESH_TOKEN + "\nexpires_in:" + EXPIRES_IN
                    + "\nlast time get tokens:" + LAST_TIME_GET_TOKENS);
        } catch (IOException e) {
            Log.e(TAG, "IOException when request token", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException when request token", e);
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException when request token", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException when close InputStream", e);
                }
            }
        }

        return isRequestTokensSucceed();
    }

    private static Header getCookie(Account account, ExtendedAuthToken extToken) {
        StringBuilder builder = new StringBuilder();
        builder.append("serviceToken=");
        builder.append(extToken.authToken);
        Log.d(TAG, "serviceToken:" + extToken.authToken);
        builder.append("; userId=");
        builder.append(account.name);
        return new BasicHeader("Cookie", builder.toString());
    }

    private static ExtendedAuthToken getExtToken() {
        Context context = MusicApplication.getApplication();
        Account account = ExtraAccountManager.getXiaomiAccount(context);
        if (account == null) {
            Log.d(TAG, "account is null.");
            return null;
        }

        AccountManager accountManager = AccountManager.get(context);
        AccountManagerFuture<Bundle> future = accountManager.getAuthToken(account, SID, null, true,
                null, null);

        String serviceToken = null;
        try {
            serviceToken = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
            if (serviceToken == null) {
                Log.e(TAG, "serviceToken is null.");
            }
        } catch (Exception e) {
            Log.e(TAG, "get extToken error: " + e.toString());
            e.printStackTrace();
            return null;
        }
        return ExtendedAuthToken.parse(serviceToken);
    }

    private static String getDeviceId() {
        Context context = MusicApplication.getApplication();
        String android_id = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        return android_id;
    }

    private static boolean isRequestTokensSucceed() {
        return !TextUtils.isEmpty(REFRESH_TOKEN) && !TextUtils.isEmpty(ACCESS_TOKEN);
    }

    private static boolean requestTokensFromStorage() {
        if (LAST_TIME_GET_TOKENS == 0) {
            LAST_TIME_GET_TOKENS = getLong(KEY_LAST_TIME_GET_TOKENS, 0);
        }

        long currTime = System.currentTimeMillis();
        if (currTime - LAST_TIME_GET_TOKENS >= DAY_OF_MILLISECONDS
                || currTime < LAST_TIME_GET_TOKENS) {
            Log.e(TAG, "tokens exceed one day.");
            return false;
        }

        String accessToken = getString(KEY_ACCESS_TOKEN, null);
        String refreshToken = getString(KEY_REFRESH_TOKEN, null);
        long expiresIn = getLong(KEY_EXPIRES_IN, 0);

        if (TextUtils.isEmpty(accessToken) || TextUtils.isEmpty(refreshToken)
                || expiresIn < DAY_OF_MILLISECONDS) {
            Log.d(TAG, "accessToken:" + accessToken == null ? "null" : accessToken);
            Log.d(TAG, "refreshToken:" + refreshToken == null ? "null" : refreshToken);
            Log.d(TAG, "expiresIn:" + expiresIn);
            Log.e(TAG, "tokens not available.");
            return false;
        }

        ACCESS_TOKEN = accessToken;
        REFRESH_TOKEN = refreshToken;
        EXPIRES_IN = expiresIn;

        return true;
    }

    //此处有同步问题，使用access_token时都从此处获取，不要保存起来
    public static String getAccessToken() {
        return ACCESS_TOKEN;
    }

    public static String getRefreshToken() {
        return REFRESH_TOKEN;
    }

    public static long getExpiresIn() {
        return EXPIRES_IN;
    }

    public static void clearTokens() {
        ACCESS_TOKEN = null;
        REFRESH_TOKEN = null;
        EXPIRES_IN = 0;
        LAST_TIME_GET_TOKENS = 0;
        putString(KEY_ACCESS_TOKEN, null);
        putString(KEY_REFRESH_TOKEN, null);
        putLong(KEY_EXPIRES_IN, 0);
        putLong(KEY_LAST_TIME_GET_TOKENS, 0);
        Log.d(TAG, "tokens are cleared.");
    }

    private static synchronized void putString(String key, String value) {
        SharedPreferences sp = MusicApplication.getApplication().getSharedPreferences(
                KEY_TOKEN_SETTINGS, 0);
        Editor editor = sp.edit();
        if (value == null) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.commit();
    }

    private static synchronized String getString(String key, String defValue) {
        SharedPreferences sp = MusicApplication.getApplication().getSharedPreferences(
                KEY_TOKEN_SETTINGS, 0);
        return sp.getString(key, defValue);
    }

    private static synchronized void putLong(String key, long value) {
        SharedPreferences sp = MusicApplication.getApplication().getSharedPreferences(
                KEY_TOKEN_SETTINGS, 0);
        Editor editor = sp.edit();
        if (value == 0) {
            editor.remove(key);
        } else {
            editor.putLong(key, value);
        }
        editor.commit();
    }

    private static synchronized long getLong(String key, long defValue) {
        SharedPreferences sp = MusicApplication.getApplication().getSharedPreferences(
                KEY_TOKEN_SETTINGS, 0);
        return sp.getLong(key, defValue);
    }
}