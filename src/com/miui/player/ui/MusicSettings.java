
package com.miui.player.ui;

import android.accounts.Account;
import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.miui.player.R;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper.AccountPermissionListener;
import com.miui.player.plugin.onlinepay.VipOrderHelper;
import com.miui.player.plugin.onlinesync.SyncPlaylist;
import com.miui.player.ui.model.AlbumListAdapter;
import com.miui.player.util.AccountUtils;
import com.miui.player.util.FolderProvider;
import com.miui.player.util.PlaylistRecoverer;
import com.miui.player.util.PreferenceCache;
import com.miui.player.util.ServiceActions;
import com.miui.player.util.ServiceActions.In;
import com.miui.player.util.Utils;

import miui.accounts.ExtraAccountManager;
import miui.v5.widget.Views;

public class MusicSettings extends PreferenceActivity implements Preference.OnPreferenceChangeListener,
        OnPreferenceClickListener, AccountPermissionListener {

    private final static boolean ACCOUNT_ENABLED = true;

    Preference mFilterCategoryPref;
    Preference mPlayAndDownload;

    private PreferenceCategory mAccountSetting;
    private OptionPreference mXiaomiAccountPreference;
    private OptionPreference mBaiduAccountPreference;
    private CheckBoxPreference mSynchronizePlaylistPreference;
    private OptionPreference mHigherMusicQualityPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.music_settings);

        findPreference(PreferenceCache.PREF_DOWNLOAD_ALBUM_OTHER).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_DOWNLOAD_LYRIC_OTHER).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_LISTEN_TO_MUSIC_OTHER).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_FILTER_BY_SIZE).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_FILTER_BY_DURATION).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_FOLDERS_UNSELECTED).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_ANDROID_ALBUM).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_PLAY_AND_DOWNLOAD).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_SCREEN_BRIGHT_WAKE).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_KEEP_QUIT_LOCATION).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_SHAKE).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_DISPLAY_LYRIC).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_DISPLAY_ALBUM).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_SHAKE_WHILE_SCREEN_ON).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_CORRECT_ID3_SETTINGS).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_AUTO_CORRECT_ID3).setOnPreferenceChangeListener(this);
        findPreference(PreferenceCache.PREF_SYNCHRONIZE_PLAYLIST).setOnPreferenceChangeListener(this);

        TwoStatePreference fadePref = (TwoStatePreference) findPreference(PreferenceCache.PREF_FADE_EFFECT_ACTIVE);

        if (PreferenceCache.IS_LPA_DECODE) {
            fadePref.setChecked(false);
            fadePref.setEnabled(false);
        } else {
            fadePref.setOnPreferenceChangeListener(this);
        }

        mFilterCategoryPref = findPreference(PreferenceCache.PREF_CATEGORY_FILTER);
        mPlayAndDownload = findPreference(PreferenceCache.PREF_PLAY_AND_DOWNLOAD);

        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setTitle(R.string.music_settings);
            bar.setHomeButtonEnabled(true);
        }

        if (Utils.isOnlineVaild()) {
            initializeMusicQualitySetting();
        } else {
            final PreferenceScreen screen = getPreferenceScreen();
            UIHelper.removeChildPreference(screen, PreferenceCache.PREF_CATEGORY_MOBILE_CONNECT_SETTINGS);
            UIHelper.removeChildPreference(screen, PreferenceCache.PREF_PLAY_AND_DOWNLOAD);
            UIHelper.removeChildPreference(screen, PreferenceCache.PREF_AUTO_CORRECT_ID3);
            UIHelper.removeChildPreference(screen, PreferenceCache.PREF_MUSIC_QUALITY);

            View view = Views.inflate(this, R.layout.music_setting_bottom_view, null, false);
            ListView listView = (ListView) findViewById(android.R.id.list);
            listView.addFooterView(view);
        }

        if (ACCOUNT_ENABLED) {
            initializeAccountSetting();
        } else {
            final PreferenceScreen screen = getPreferenceScreen();
            UIHelper.removeChildPreference(screen, PreferenceCache.PREF_ACCOUNT_SETTING);
        }

    }

    private void initializeMusicQualitySetting() {
        mHigherMusicQualityPreference = (OptionPreference) findPreference(PreferenceCache.PREF_HIGHER_MUSIC_QUALITY);
        mHigherMusicQualityPreference.setOnPreferenceClickListener(this);
    }

    private void refreshMusicQuality() {
        if (!Utils.isOnlineVaild()) {
            return;
        }

        if (AccountPermissionHelper.allowUHDMusic()) {
            mHigherMusicQualityPreference.setMiuiLabel(R.string.enabled);
        } else if (AccountPermissionHelper.hasInitialized() && AccountPermissionHelper.hasBoughtVip()) {
            mHigherMusicQualityPreference.setMiuiLabel(R.string.expired);
        } else {
            mHigherMusicQualityPreference.setMiuiLabel(null);
        }

        String startDate = AccountPermissionHelper.getVipStartDate();
        String endDate = AccountPermissionHelper.getVipEndDate();
        int vipRemainDays = AccountPermissionHelper.getVipRemainDays();
        if (vipRemainDays >= 0) {
            mHigherMusicQualityPreference.setSummary(getResources().getQuantityString(
                    R.plurals.Nexpired_remind, vipRemainDays, vipRemainDays));
        } else if (!TextUtils.isEmpty(startDate) && !TextUtils.isEmpty(endDate)) {
            mHigherMusicQualityPreference.setSummary(getString(R.string.valid_period) +
                    getString(R.string.period_format, startDate, endDate));
        } else {
            mHigherMusicQualityPreference.setSummary(R.string.higher_quality_music_summary);
        }
    }

    private void initializeAccountSetting() {
        mAccountSetting = (PreferenceCategory) findPreference(PreferenceCache.PREF_ACCOUNT_SETTING);
        mXiaomiAccountPreference = (OptionPreference) findPreference(PreferenceCache.PREF_XIAOMI_ACCOUNT_SETTING);
        mBaiduAccountPreference = (OptionPreference) findPreference(PreferenceCache.PREF_BAIDU_ACCOUNT_SETTING);
        mSynchronizePlaylistPreference = (CheckBoxPreference) findPreference(PreferenceCache.PREF_SYNCHRONIZE_PLAYLIST);
        mXiaomiAccountPreference.setOnPreferenceClickListener(this);
        mSynchronizePlaylistPreference.setOnPreferenceChangeListener(this);
        // 第一期不做明绑，将明绑选项去除
        mAccountSetting.removePreference(mBaiduAccountPreference);
    }

    /**
     * Warning : Just call when ACCOUNT_ENABLED == true, or will FC.
     */
    private void refreshXiaomiAccount() {
        Account xiaomiAccount = ExtraAccountManager.getXiaomiAccount(MusicSettings.this);
        if (xiaomiAccount != null) {
            mAccountSetting.addPreference(mSynchronizePlaylistPreference);
            mSynchronizePlaylistPreference.setChecked(PreferenceCache.getPrefAsBoolean(this,
                    PreferenceCache.PREF_SYNCHRONIZE_PLAYLIST));
            mXiaomiAccountPreference.setMiuiLabel(xiaomiAccount.name);
        } else {
            mAccountSetting.removePreference(mSynchronizePlaylistPreference);
            mXiaomiAccountPreference.setMiuiLabel(R.string.not_login);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccountPermissionHelper.addListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        registerReceiver(mMountReceiver, filter);
        mMountReceiver.onReceive(this, null);
        if (ACCOUNT_ENABLED) {
            refreshXiaomiAccount();
        }

        refreshMusicQuality();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mMountReceiver);
        AccountPermissionHelper.removeListener(this);
        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        String key = pref.getKey();

        PreferenceCache.put(this, key, objValue);

        if (key.equals(PreferenceCache.PREF_DISPLAY_ALBUM) ||
                key.equals(PreferenceCache.PREF_ANDROID_ALBUM) ||
                key.equals(PreferenceCache.PREF_DOWNLOAD_ALBUM_OTHER)) {
            Intent intent = new Intent(ServiceActions.In.UPDATE_META_ACTION);
            intent.putExtra(In.CMDNAME, In.CMDALBUM);
            sendBroadcast(intent);
            if (key.equals(PreferenceCache.PREF_ANDROID_ALBUM)) {
                AlbumListAdapter.removeCache();
            }
        } else if (key.equals(PreferenceCache.PREF_DISPLAY_LYRIC)) {
            Intent intent = new Intent(ServiceActions.In.UPDATE_META_ACTION);
            intent.putExtra(In.CMDNAME, In.CMDLYRIC);
            sendBroadcast(intent);
        } else if (key.equals(PreferenceCache.PREF_SHAKE)) {
            Intent intent = new Intent(ServiceActions.In.UPDATE_SHAKE);
            sendBroadcast(intent);
        } else if (key.equals(PreferenceCache.PREF_FILTER_BY_SIZE) ||
                key.equals(PreferenceCache.PREF_FILTER_BY_SIZE_PROGRESS) ||
                key.equals(PreferenceCache.PREF_FILTER_BY_DURATION) ||
                key.equals(PreferenceCache.PREF_FILTER_BY_DURATION_PROGRESS)) {
            PlaylistRecoverer.markForceRecover();
            FolderProvider.markForceUpdate();
        } else if (key.equals(PreferenceCache.PREF_SYNCHRONIZE_PLAYLIST)) {
            Log.d("SyncPlaylist", "from settings.");
            SyncPlaylist.requestSync();
        }
        return true;
    }

    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isExternalStoreageMounted = Utils.isExternalStorageMounted();
            mFilterCategoryPref.setEnabled(isExternalStoreageMounted);
            mPlayAndDownload.setEnabled(isExternalStoreageMounted);
        }

    };

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mHigherMusicQualityPreference) {
            startActivity(new Intent(this, VipRecommendActivity.class));
            return true;
        } else if (preference == mXiaomiAccountPreference) {
            Account xiaomiAccount = ExtraAccountManager.getXiaomiAccount(MusicSettings.this);
            if (xiaomiAccount == null) {
                AccountUtils.loginXiaomiAccount(MusicSettings.this, null);
            } else {
                // 去“小米云服务”页面
                Intent intent = new Intent("android.settings.XIAOMI_ACCOUNT_SYNC_SETTINGS");
                startActivity(intent);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onPermissionChanged(int allowQuality) {
        if (isResumed()) {
            refreshMusicQuality();
        }
    }

    @Override
    public void onPeriodChanged(String startTime, String endTime) {
        if (isResumed()) {
            refreshMusicQuality();
        }
    }
}
