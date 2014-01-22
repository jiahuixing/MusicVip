package com.miui.player.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.miui.player.R;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper;
import com.miui.player.plugin.onlinepay.AccountPermissionHelper.AccountPermissionListener;
import com.miui.player.plugin.onlinepay.VipOrderHelper;
import com.miui.player.ui.controller.SuspendBar;

public class VipRecommendActivity extends Activity implements AccountPermissionListener {

    private SuspendBar mSuspendBar;
    private View mVipSummary;
    private TextView mSummary;
    private TextView mVipTitle;
    private TextView mVipData;
    private Button mBuyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vip_recommend);

        mVipSummary = findViewById(R.id.vip_summary);
        mSummary = (TextView) findViewById(R.id.summary);
        mVipTitle = (TextView) findViewById(R.id.vip_data_title);
        mVipData = (TextView) findViewById(R.id.vip_data);
        mBuyButton = (Button) findViewById(R.id.buy_vip);

        mSuspendBar = new SuspendBar(this);

        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setTitle(R.string.vip_recommend_title);
            bar.setHomeButtonEnabled(true);
        }

        Button buyButton = (Button) findViewById(R.id.buy_vip);
        buyButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                VipOrderHelper.createOrderAsync(VipRecommendActivity.this,
                        Long.toString(VipOrderHelper.DEFAULT_PRODUCT_ID));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccountPermissionHelper.addListener(this);
        refreshVipPermission();
        refreshVipData();
        refreshVisible();
        mSuspendBar.refresh();
    }

    @Override
    protected void onPause() {
        AccountPermissionHelper.removeListener(this);
        super.onPause();
    }

    private void refreshVipPermission() {
        if (AccountPermissionHelper.allowUHDMusic()) {
            mVipTitle.setVisibility(View.VISIBLE);
            mVipTitle.setText(R.string.vip_bought);
            mBuyButton.setText(R.string.vip_recommend_rebuy);
        } else if (AccountPermissionHelper.hasBoughtVip()) {
            mVipTitle.setVisibility(View.VISIBLE);
            mVipTitle.setText(R.string.vip_expired);
            mBuyButton.setText(R.string.vip_recommend_rebuy);
        } else {
            mVipTitle.setVisibility(View.GONE);
            mBuyButton.setText(R.string.vip_recommend_buy);
        }
    }

    private void refreshVipData() {
        // 刷新有效期
        String startTime = AccountPermissionHelper.getVipStartDate();
        String endTime = AccountPermissionHelper.getVipEndDate();
        mVipData = (TextView) findViewById(R.id.vip_data);
        if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
            CharSequence validPeriod = getString(R.string.valid_period, startTime, endTime);
            CharSequence period = getString(R.string.period_format, startTime, endTime);
            String colorPattern = "<font color=\"red\">%s</font>";
            period = Html.fromHtml(String.format(colorPattern, period));
            SpannableStringBuilder style = new SpannableStringBuilder();
            style.append(validPeriod);
            style.append(period);
            mVipData.setText(style);
            mVipData.setVisibility(View.VISIBLE);
        } else {
            mVipData.setVisibility(View.GONE);
        }
    }

    private void refreshVisible() {
        if (mVipTitle.getVisibility() == View.VISIBLE && mVipData.getVisibility() == View.VISIBLE) {
            mVipSummary.setVisibility(View.VISIBLE);
            mSummary.setVisibility(View.GONE);
        } else {
            mVipSummary.setVisibility(View.GONE);
            mSummary.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPermissionChanged(int allowQuality) {
        if (isResumed()) {
            refreshVipPermission();
            refreshVisible();
        }
    }

    @Override
    public void onPeriodChanged(String startTime, String endTime) {
        if (isResumed()) {
            refreshVipData();
            refreshVisible();
        }
    }
}