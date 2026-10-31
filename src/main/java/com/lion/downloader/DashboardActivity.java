package com.lion.downloader;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.PlaybackParams;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.util.Size;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardActivity extends Activity {

    private static final int POOL_SIZE = 12;

    // 4 Separate Full-Screen Pages
    private RelativeLayout rootDashboard;
    private FrameLayout fullscreenHost;
    private RelativeLayout playerContainerBox;
    private ViewGroup playerOriginalParent;
    private ViewGroup.LayoutParams playerOriginalLayoutParams;
    private int playerOriginalIndex = -1;
    private ScrollView pageHome, pageSettings;
    private LinearLayout pageDownloads, pageGallery;
    private LinearLayout downloadsListContainer, galleryListContainer, settingsContentContainer;
    private TextView tvActiveDownloadStatus, tvGalleryHeader, tvSelectedFormatBtn;

    // Home Views
    private HorizontalScrollView hsvRow1, hsvRow2;
    private LinearLayout row1Container, row2Container;
    private EditText etUrl, etVideoSearch;
    private VideoView realVideoView;
    private ImageView ivPlayerPreview;
    private TextView tvCurrentTime, tvTotalTime, tvQualityBadge, tvActiveTitle, tvActiveSub, tvPlatformBadge, btnSpeedControl;
    private SeekBar playerSeekBar;
    private View btnCenterPlay;

    // Settings & Preferences State
    private SharedPreferences prefs;
    private boolean isDarkMode = true;
    private boolean forceThemeOverride = false;
    private boolean autoPlayNext = true;
    private boolean hwAcceleration = true;
    private String selectedFormatLabel = "1080p_MP4";
    private String selectedFormatExt = ".mp4";
    private float currentSpeed = 1.0f;

    // Active Video State
    private String currentPlatform = "ALL";
    private String currentVideoId = "";
    private String currentVideoUrl = "";
    private String currentDirectMp4 = "";
    private String currentTitle = "";
    private int selectedPoolIndex = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isNativeVideoPrepared = false;
    private volatile boolean activityDestroyed = false;
    private volatile int feedRequestSequence = 0;
    private boolean isPlayerFullscreen = false;

    // Feed Pool
    private String[] poolIds = new String[POOL_SIZE];
    private String[] poolTitles = new String[POOL_SIZE];
    private String[] poolMetas = new String[POOL_SIZE];
    private String[] poolDurs = new String[POOL_SIZE];
    private String[] poolPlatforms = new String[POOL_SIZE];
    private String[] poolDirectMp4s = new String[POOL_SIZE];
    private Bitmap[] poolBitmaps = new Bitmap[POOL_SIZE];

    private int row1LoopCursor = 0;
    private int row2LoopCursor = 6;
    private boolean isAppendingRow1 = false;
    private boolean isAppendingRow2 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = getSharedPreferences("nexora_pro_settings", MODE_PRIVATE);
        selectedFormatLabel = "ORIGINAL";
        selectedFormatExt = "";
        if (!"ORIGINAL".equals(prefs.getString("default_fmt", "ORIGINAL"))) {
            prefs.edit().putString("default_fmt", "ORIGINAL").apply();
        }
        autoPlayNext = prefs.getBoolean("autoplay_next", true);
        hwAcceleration = prefs.getBoolean("hw_accel", true);

        getSharedPreferences("nexora_permanent_auth", MODE_PRIVATE).edit().putBoolean("boxes_ticked_and_started", true).apply();
        bindViews();
        tvSelectedFormatBtn.setText("DL " + selectedFormatLabel.split("_")[0]);

        applyPhoneSystemTheme(getResources().getConfiguration());
        setupInfiniteLoopRows();
        setupAllControls();
        buildFullSettingsPage();

        loadUnifiedMixedFeed("Trending Viral Songs 2026");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!forceThemeOverride) {
            applyPhoneSystemTheme(newConfig);
        }
    }

    // =========================================================
    // 1. MULTI-PAGE NAVIGATION (HOME / DOWNLOADS / GALLERY / SETTINGS)
    // =========================================================
    private void switchPage(int pageIndex) {
        pageHome.setVisibility(pageIndex == 0 ? View.VISIBLE : View.GONE);
        pageDownloads.setVisibility(pageIndex == 1 ? View.VISIBLE : View.GONE);
        pageGallery.setVisibility(pageIndex == 2 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(pageIndex == 3 ? View.VISIBLE : View.GONE);

        findViewById(R.id.navIconHome).setBackgroundResource(pageIndex == 0 ? R.drawable.frame_icon_active : R.drawable.frame_icon_slot);
        findViewById(R.id.navIconDownloads).setBackgroundResource(pageIndex == 1 ? R.drawable.frame_icon_active : R.drawable.frame_icon_slot);
        findViewById(R.id.navIconGallery).setBackgroundResource(pageIndex == 2 ? R.drawable.frame_icon_active : R.drawable.frame_icon_slot);
        findViewById(R.id.navIconSettings).setBackgroundResource(pageIndex == 3 ? R.drawable.frame_icon_active : R.drawable.frame_icon_slot);

        if (pageIndex == 1) {
            refreshDownloadsManagerPage();
        } else if (pageIndex == 2) {
            scanAndPopulatePhoneGalleryPage();
        } else if (pageIndex == 3) {
            buildFullSettingsPage();
        }
    }

    // =========================================================
    // 2. AUTO DARK / LIGHT MODE SYNC
    // =========================================================
    private void applyPhoneSystemTheme(Configuration config) {
        if (!forceThemeOverride) {
            int nightModeFlags = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
        }
        applyThemeColors();
    }

    private void applyThemeColors() {
        int bgRoot = isDarkMode ? Color.parseColor("#03060C") : Color.parseColor("#F2F6FC");
        int textPrimary = isDarkMode ? Color.parseColor("#FFFFFF") : Color.parseColor("#0D1B2A");
        int textSecondary = isDarkMode ? Color.parseColor("#00E5FF") : Color.parseColor("#0066CC");
        int hintColor = isDarkMode ? Color.parseColor("#6C829E") : Color.parseColor("#557799");
        int boxDrawable = isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_pill;
        int panelDrawable = isDarkMode ? R.drawable.frame_download_panel : R.drawable.frame_light_card;

        rootDashboard.setBackgroundResource(isDarkMode ? R.drawable.bg_nexora_wallpaper : R.drawable.bg_nexora_wallpaper_light);
        findViewById(R.id.boxUrlBar).setBackgroundResource(isDarkMode ? R.drawable.frame_search_bar : R.drawable.frame_light_card);
        findViewById(R.id.boxSearchBar).setBackgroundResource(boxDrawable);
        findViewById(R.id.boxActionButtons).setBackgroundResource(boxDrawable);
        findViewById(R.id.boxInfoBar).setBackgroundResource(boxDrawable);
        findViewById(R.id.bottomNav).setBackgroundResource(panelDrawable);

        etUrl.setTextColor(textPrimary);
        etUrl.setHintTextColor(hintColor);
        etVideoSearch.setTextColor(textSecondary);
        etVideoSearch.setHintTextColor(hintColor);
        tvActiveTitle.setTextColor(textPrimary);
        tvActiveSub.setTextColor(textSecondary);

        ((TextView) findViewById(R.id.txtTabYt)).setTextColor(textPrimary);
        ((TextView) findViewById(R.id.txtTabIg)).setTextColor(textPrimary);
        ((TextView) findViewById(R.id.txtTabTk)).setTextColor(textPrimary);
        ((TextView) findViewById(R.id.txtTabX)).setTextColor(textPrimary);

        refreshCardContainerTheme(row1Container);
        refreshCardContainerTheme(row2Container);
    }

    private void refreshCardContainerTheme(LinearLayout container) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View card = container.getChildAt(i);
            if (card instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) card;
                if (ll.getChildCount() >= 3) {
                    ll.getChildAt(0).setBackgroundResource(isDarkMode ? R.drawable.frame_neon_blue : R.drawable.frame_light_card);
                    ((TextView) ll.getChildAt(1)).setTextColor(isDarkMode ? Color.WHITE : Color.parseColor("#0D1B2A"));
                    ((TextView) ll.getChildAt(2)).setTextColor(isDarkMode ? Color.parseColor("#80B3FF") : Color.parseColor("#455A64"));
                }
            }
        }
    }

    private void bindViews() {
        rootDashboard = findViewById(R.id.rootDashboard);
        fullscreenHost = findViewById(R.id.fullscreenHost);
        playerContainerBox = findViewById(R.id.playerContainerBox);
        pageHome = findViewById(R.id.pageHome);
        pageDownloads = findViewById(R.id.pageDownloads);
        pageGallery = findViewById(R.id.pageGallery);
        pageSettings = findViewById(R.id.pageSettings);

        downloadsListContainer = findViewById(R.id.downloadsListContainer);
        galleryListContainer = findViewById(R.id.galleryListContainer);
        settingsContentContainer = findViewById(R.id.settingsContentContainer);
        tvActiveDownloadStatus = findViewById(R.id.tvActiveDownloadStatus);
        tvGalleryHeader = findViewById(R.id.tvGalleryHeader);
        tvSelectedFormatBtn = findViewById(R.id.tvSelectedFormatBtn);

        hsvRow1 = findViewById(R.id.hsvRow1);
        hsvRow2 = findViewById(R.id.hsvRow2);
        row1Container = findViewById(R.id.row1Container);
        row2Container = findViewById(R.id.row2Container);
        etUrl = findViewById(R.id.etUrl);
        etVideoSearch = findViewById(R.id.etVideoSearch);
        realVideoView = findViewById(R.id.realVideoView);
        ivPlayerPreview = findViewById(R.id.ivPlayerPreview);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvQualityBadge = findViewById(R.id.tvQualityBadge);
        tvActiveTitle = findViewById(R.id.tvActiveTitle);
        tvActiveSub = findViewById(R.id.tvActiveSub);
        tvPlatformBadge = findViewById(R.id.tvPlatformBadge);
        btnSpeedControl = findViewById(R.id.btnSpeedControl);
        playerSeekBar = findViewById(R.id.playerSeekBar);
        btnCenterPlay = findViewById(R.id.btnCenterPlay);
    }

    // =========================================================
    // 3. THREE-DOT (⋮) VIDEO & AUDIO FORMAT + QUALITY SELECTOR
    // =========================================================
    private void showThreeDotFormatSelectorDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(isDarkMode ? R.drawable.frame_download_panel : R.drawable.frame_light_card);
        int p = dp(14);
        root.setPadding(p, p, p, p);
        root.setLayoutParams(new ViewGroup.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("🎬 Select Video or Audio Format & Quality");
        title.setTextSize(13f);
        title.setTextColor(isDarkMode ? Color.parseColor("#00E5FF") : Color.parseColor("#0066CC"));
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // Transcoding is not implemented in this build. Offer only a truthful source-preserving option.
        final String[][] options = {
            {"ORIGINAL • Save the source media unchanged (no conversion)", "ORIGINAL", ""}
        };

        for (int i = 0; i < options.length; i++) {
            final String desc = options[i][0];
            final String code = options[i][1];
            final String ext = options[i][2];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_pill);
            item.setPadding(dp(10), dp(9), dp(10), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            item.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(desc);
            tv.setTextSize(10.5f);
            tv.setTextColor(desc.startsWith("AUDIO") ? Color.parseColor("#80E5FF") : (isDarkMode ? Color.WHITE : Color.BLACK));

            TextView badge = new TextView(this);
            badge.setBackgroundResource(R.drawable.chip_active);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setText("Download");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(9f);

            item.addView(tv);
            item.addView(badge);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFormatLabel = code;
                    selectedFormatExt = ext;
                    prefs.edit().putString("default_fmt", code).apply();
                    tvSelectedFormatBtn.setText("DL " + code.split("_")[0]);
                    dialog.dismiss();
                    downloadCurrentSelectedVideo(code, ext);
                }
            });
            list.addView(item);
        }

        sv.addView(list);
        root.addView(sv);

        Button btnClose = new Button(this);
        btnClose.setText("Cancel");
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        root.addView(btnClose);

        dialog.setContentView(root);
        dialog.show();
    }

    // =========================================================
    // 4. INFINITE HORIZONTAL LOOP ROWS
    // =========================================================
    private void setupInfiniteLoopRows() {
        row1Container.removeAllViews();
        row2Container.removeAllViews();
        row1LoopCursor = 0;
        row2LoopCursor = 6;

        for (int i = 0; i < 10; i++) {
            appendLoopCard(row1Container, row1LoopCursor % POOL_SIZE);
            row1LoopCursor++;
            appendLoopCard(row2Container, row2LoopCursor % POOL_SIZE);
            row2LoopCursor++;
        }

        hsvRow1.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int maxScroll = row1Container.getMeasuredWidth() - hsvRow1.getWidth();
                if (maxScroll > 0 && scrollX >= maxScroll - 260 && !isAppendingRow1) {
                    isAppendingRow1 = true;
                    for (int k = 0; k < 4; k++) {
                        appendLoopCard(row1Container, row1LoopCursor % POOL_SIZE);
                        row1LoopCursor++;
                    }
                    while (row1Container.getChildCount() > 36) {
                        row1Container.removeViewAt(0);
                    }
                    isAppendingRow1 = false;
                }
            }
        });

        hsvRow2.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int maxScroll = row2Container.getMeasuredWidth() - hsvRow2.getWidth();
                if (maxScroll > 0 && scrollX >= maxScroll - 260 && !isAppendingRow2) {
                    isAppendingRow2 = true;
                    for (int k = 0; k < 4; k++) {
                        appendLoopCard(row2Container, row2LoopCursor % POOL_SIZE);
                        row2LoopCursor++;
                    }
                    while (row2Container.getChildCount() > 36) {
                        row2Container.removeViewAt(0);
                    }
                    isAppendingRow2 = false;
                }
            }
        });
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private void appendLoopCard(LinearLayout parent, final int poolIdx) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.MATCH_PARENT);
        cardLp.rightMargin = dp(7);
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag(poolIdx);

        RelativeLayout thumbBox = new RelativeLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        thumbBox.setLayoutParams(boxLp);
        thumbBox.setBackgroundResource(isDarkMode ? R.drawable.frame_neon_blue : R.drawable.frame_light_card);
        int pad = dp(2);
        thumbBox.setPadding(pad, pad, pad, pad);

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (poolBitmaps[poolIdx] != null) {
            iv.setImageBitmap(poolBitmaps[poolIdx]);
        }

        TextView tvDur = new TextView(this);
        RelativeLayout.LayoutParams durLp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        durLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        durLp.setMargins(dp(4), dp(4), dp(4), dp(4));
        tvDur.setLayoutParams(durLp);
        tvDur.setBackgroundResource(R.drawable.badge_duration);
        tvDur.setPadding(dp(4), dp(1), dp(4), dp(1));
        tvDur.setTextColor(Color.WHITE);
        tvDur.setTextSize(7f);
        tvDur.setText(poolDurs[poolIdx] != null ? poolDurs[poolIdx] : "HD");

        thumbBox.addView(iv);
        thumbBox.addView(tvDur);

        TextView tvTitle = new TextView(this);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setTextSize(8.5f);
        tvTitle.setTextColor(isDarkMode ? Color.WHITE : Color.parseColor("#0D1B2A"));
        tvTitle.setText(poolTitles[poolIdx] != null ? poolTitles[poolIdx] : "Loading...");

        TextView tvMeta = new TextView(this);
        tvMeta.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvMeta.setSingleLine(true);
        tvMeta.setEllipsize(TextUtils.TruncateAt.END);
        tvMeta.setTextSize(7f);
        tvMeta.setTextColor(isDarkMode ? Color.parseColor("#80B3FF") : Color.parseColor("#455A64"));
        tvMeta.setText(poolMetas[poolIdx] != null ? poolMetas[poolIdx] : "Tap to Play");

        card.addView(thumbBox);
        card.addView(tvTitle);
        card.addView(tvMeta);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPoolCardTapped(poolIdx);
            }
        });

        parent.addView(card);
    }

    private void syncPoolSlotToVisibleCards(int poolIdx) {
        LinearLayout[] containers = {row1Container, row2Container};
        for (LinearLayout container : containers) {
            if (container == null) continue;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer && ((Integer) tag) == poolIdx) {
                    LinearLayout card = (LinearLayout) child;
                    RelativeLayout thumbBox = (RelativeLayout) card.getChildAt(0);
                    ImageView iv = (ImageView) thumbBox.getChildAt(0);
                    TextView tvDur = (TextView) thumbBox.getChildAt(1);
                    TextView tvTitle = (TextView) card.getChildAt(1);
                    TextView tvMeta = (TextView) card.getChildAt(2);

                    if (poolBitmaps[poolIdx] != null) iv.setImageBitmap(poolBitmaps[poolIdx]);
                    if (poolDurs[poolIdx] != null) tvDur.setText(poolDurs[poolIdx]);
                    if (poolTitles[poolIdx] != null) tvTitle.setText(poolTitles[poolIdx]);
                    if (poolMetas[poolIdx] != null) tvMeta.setText(poolMetas[poolIdx]);
                }
            }
        }
    }

    // =========================================================
    // 5. SERVER FEED LOADER (STRICT PLATFORM SEPARATION)
    // =========================================================
    private void loadUnifiedMixedFeed(final String query) {
        final String requestedPlatform = currentPlatform;
        final int requestId = ++feedRequestSequence;
        tvActiveSub.setText("Loading " + requestedPlatform + " Feed: " + query);

        if ("INSTAGRAM".equals(requestedPlatform) || "X".equals(requestedPlatform)) {
            tvQualityBadge.setText("NOT CONNECTED");
            tvActiveSub.setText(requestedPlatform + " feed is unavailable: no real provider integration is configured. No TikTok results will be shown as " + requestedPlatform + ".");
            Toast.makeText(this, requestedPlatform + " feed integration is not configured.", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (requestedPlatform.equals("TIKTOK")) {
                    fetchFromShortVideoServer(query, 0, POOL_SIZE, 1, requestId);
                    return;
                }

                if (requestedPlatform.equals("ALL")) {
                    // Only sources actually integrated in this build: TikTok + YouTube.
                    fetchFromShortVideoServer(query, 0, POOL_SIZE, 2, requestId);
                }

                try {
                    String searchUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + "&hl=en";
                    HttpURLConnection conn = (HttpURLConnection) new URL(searchUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    ArrayList<String> ytIds = new ArrayList<>();
                    Pattern p = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"");
                    while ((line = br.readLine()) != null && ytIds.size() < POOL_SIZE) {
                        Matcher m = p.matcher(line);
                        while (m.find() && ytIds.size() < POOL_SIZE) {
                            String id = m.group(1);
                            if (!ytIds.contains(id)) ytIds.add(id);
                        }
                    }
                    br.close();

                    for (int i = 0; i < ytIds.size() && i < POOL_SIZE; i++) {
                        if (requestedPlatform.equals("ALL") && i % 2 == 0) continue;
                        if (requestId != feedRequestSequence) return;
                        loadSingleYouTubeIntoSlot(i, ytIds.get(i), requestId);
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void fetchFromShortVideoServer(String query, int startSlot, int limit, int step, final int requestId) {
        try {
            String tkUrl = "https://www.tikwm.com/api/feed/search?keywords=" + URLEncoder.encode(query, "UTF-8") + "&count=12";
            HttpURLConnection conn = (HttpURLConnection) new URL(tkUrl).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject dataObj = root.optJSONObject("data");
                JSONArray videos = dataObj != null ? dataObj.optJSONArray("videos") : null;

                if (videos != null && videos.length() > 0) {
                    for (int i = startSlot; i < limit; i += step) {
                        if (requestId != feedRequestSequence) return;
                        JSONObject v = videos.getJSONObject(i % videos.length());
                        final String vid = v.optString("video_id", "tk_" + i);
                        final String rawTitle = v.optString("title", "Viral Video #" + (i + 1));
                        String playUrl = v.optString("play", "");
                        if (playUrl.startsWith("/")) playUrl = "https://www.tikwm.com" + playUrl;
                        final String finalPlayUrl = playUrl;
                        String coverUrl = v.optString("cover", "");
                        if (coverUrl.startsWith("/")) coverUrl = "https://www.tikwm.com" + coverUrl;
                        final int durSec = v.optInt("duration", 15);
                        final String tag = "TIKTOK";
                        final Bitmap bmp = downloadBitmap(coverUrl);
                        final int slot = i;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!canUpdateUi() || requestId != feedRequestSequence) return;
                                poolIds[slot] = vid;
                                poolTitles[slot] = rawTitle.isEmpty() ? (tag + " Clip") : rawTitle;
                                poolMetas[slot] = tag + " • Direct MP4";
                                poolDurs[slot] = tag + " • " + formatMs(durSec * 1000);
                                poolPlatforms[slot] = tag;
                                poolDirectMp4s[slot] = finalPlayUrl;
                                poolBitmaps[slot] = bmp;
                                syncPoolSlotToVisibleCards(slot);
                            }
                        });
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadSingleYouTubeIntoSlot(final int slot, final String videoId, final int requestId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String title = "YouTube Video (" + videoId + ")";
                String author = "YouTube • HD";
                try {
                    String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
                    HttpURLConnection c = (HttpURLConnection) new URL(oembedUrl).openConnection();
                    c.setConnectTimeout(4500);
                    if (c.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = r.readLine()) != null) sb.append(l);
                        r.close();
                        JSONObject json = new JSONObject(sb.toString());
                        title = json.optString("title", title);
                        author = json.optString("author_name", "YouTube") + " • YOUTUBE";
                    }
                } catch (Exception ignored) {}

                final Bitmap bmp = downloadBitmap("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
                final String finalTitle = title;
                final String finalAuthor = author;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!canUpdateUi() || requestId != feedRequestSequence) return;
                        poolIds[slot] = videoId;
                        poolTitles[slot] = finalTitle;
                        poolMetas[slot] = finalAuthor;
                        poolDurs[slot] = "YT HD";
                        poolPlatforms[slot] = "YOUTUBE";
                        poolDirectMp4s[slot] = "";
                        poolBitmaps[slot] = bmp;
                        syncPoolSlotToVisibleCards(slot);
                    }
                });
            }
        }).start();
    }

    private Bitmap downloadBitmap(String urlStr) {
        try {
            if (urlStr == null || urlStr.isEmpty()) return null;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            InputStream is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // 6. MULTI-STAGE STREAM RESOLVER (NO STREAM ERROR / NO DL FAILED!)
    // =========================================================
    private void onPoolCardTapped(final int poolIdx) {
        switchPage(0);
        selectedPoolIndex = poolIdx;
        currentVideoId = poolIds[poolIdx] != null ? poolIds[poolIdx] : "";
        currentTitle = poolTitles[poolIdx] != null ? poolTitles[poolIdx] : "NEXORA Video";
        currentDirectMp4 = poolDirectMp4s[poolIdx] != null ? poolDirectMp4s[poolIdx] : "";
        String cardPlat = poolPlatforms[poolIdx] != null ? poolPlatforms[poolIdx] : "MIX";

        tvPlatformBadge.setText(cardPlat.length() > 3 ? cardPlat.substring(0, 2) : cardPlat);
        tvActiveTitle.setText(currentTitle);
        tvActiveSub.setText("Connecting to Direct Stream Server...");
        tvQualityBadge.setText("CONNECTING...");

        if (poolBitmaps[poolIdx] != null) {
            ivPlayerPreview.setVisibility(View.VISIBLE);
            ivPlayerPreview.setImageBitmap(poolBitmaps[poolIdx]);
        }

        pageHome.smoothScrollTo(0, 0);

        if (currentDirectMp4 != null && !currentDirectMp4.isEmpty()) {
            currentVideoUrl = currentDirectMp4;
            etUrl.setText(currentDirectMp4);
            playDirectMp4InNativeView(currentDirectMp4);
        } else {
            currentVideoUrl = "https://www.youtube.com/watch?v=" + currentVideoId;
            etUrl.setText(currentVideoUrl);
            openOfficialYouTube(currentVideoUrl);
        }
    }

    /**
     * YouTube links are intentionally handed to the official client/browser.
     * The app must not attempt to bypass YouTube access controls or extract
     * protected streams. Direct, user-authorized media URLs remain supported.
     */
    private void openOfficialYouTube(final String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            tvQualityBadge.setText("YOUTUBE");
            tvActiveSub.setText("Opened in the official YouTube app/browser.");
        } catch (Exception e) {
            tvQualityBadge.setText("OPEN ERROR");
            tvActiveSub.setText("No app can open this YouTube link.");
        }
    }

private void playDirectMp4InNativeView(final String mp4UrlOrPath) {
        try {
            if (mp4UrlOrPath == null || mp4UrlOrPath.trim().isEmpty()) {
                tvQualityBadge.setText("INVALID MEDIA");
                tvActiveSub.setText("The selected media path is empty.");
                return;
            }
            final String mediaValue = mp4UrlOrPath.trim();
            final boolean isHttps = mediaValue.regionMatches(true, 0, "https://", 0, 8);
            final boolean isContentUri = mediaValue.regionMatches(true, 0, "content://", 0, 10);
            final File localFile = (isHttps || isContentUri) ? null : new File(mediaValue);
            if (!isHttps && !isContentUri && (!localFile.isFile() || !localFile.canRead())) {
                tvQualityBadge.setText("MEDIA UNAVAILABLE");
                tvActiveSub.setText("The local media file cannot be read by this app.");
                return;
            }
            if (realVideoView.isPlaying()) realVideoView.stopPlayback();
            isNativeVideoPrepared = false;
            btnCenterPlay.setVisibility(View.GONE);
            realVideoView.setVisibility(View.VISIBLE);
            if (isHttps || isContentUri) {
                realVideoView.setVideoURI(Uri.parse(mediaValue));
            } else {
                realVideoView.setVideoPath(localFile.getAbsolutePath());
            }
            realVideoView.requestFocus();

            realVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isNativeVideoPrepared = true;
                    ivPlayerPreview.setVisibility(View.GONE);
                    btnCenterPlay.setVisibility(View.GONE);
                    int dur = realVideoView.getDuration();
                    playerSeekBar.setMax(dur > 0 ? dur : 100);
                    tvTotalTime.setText(formatMs(dur));
                    tvQualityBadge.setText("PLAYING HD");
                    tvActiveSub.setText("Playing: " + currentTitle);
                    mp.setLooping(!autoPlayNext);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSpeed != 1.0f) {
                        try {
                            mp.setPlaybackParams(new PlaybackParams().setSpeed(currentSpeed));
                        } catch (Exception ignored) {}
                    }
                    realVideoView.start();
                    startTimerLoop();
                }
            });

            realVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (autoPlayNext) {
                        int next = (selectedPoolIndex + 1) % POOL_SIZE;
                        onPoolCardTapped(next);
                    }
                }
            });

            realVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    isNativeVideoPrepared = false;
                    tvQualityBadge.setText("PLAYBACK ERROR");
                    tvActiveSub.setText("MediaPlayer error " + what + "/" + extra + ". The direct URL may be expired or unsupported.");
                    try { mp.reset(); } catch (Exception ignored) {}
                    return true;
                }
            });
        } catch (Exception e) {
            isNativeVideoPrepared = false;
            tvQualityBadge.setText("PLAYBACK ERROR");
            tvActiveSub.setText("Playback failed: " + e.getMessage());
        }
    }

    private void startTimerLoop() {
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (realVideoView != null && realVideoView.isPlaying() && isNativeVideoPrepared) {
                    int pos = realVideoView.getCurrentPosition();
                    playerSeekBar.setProgress(pos);
                    tvCurrentTime.setText(formatMs(pos));
                    timerHandler.postDelayed(this, 500);
                }
            }
        }, 500);
    }

    private String formatMs(int ms) {
        if (ms <= 0) return "00:00";
        int totalSec = ms / 1000;
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    // =========================================================
    // 7. DIRECT HTTP STREAM DOWNLOADER + GALLERY SYNC
    // =========================================================
    private void downloadCurrentSelectedVideo(final String fmtLabel, final String fmtExt) {
        if (currentTitle == null || currentTitle.isEmpty()) {
            Toast.makeText(this, "Select a video first!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isYouTubeUrl(currentVideoUrl) || (currentDirectMp4 == null || currentDirectMp4.isEmpty()) && !currentVideoId.isEmpty()) {
            Toast.makeText(this, "YouTube downloads are unavailable. Use a direct URL for content you own or are licensed to download.", Toast.LENGTH_LONG).show();
            tvQualityBadge.setText("YOUTUBE");
            tvActiveSub.setText("Open YouTube in its official app/browser; direct authorized URLs are supported here.");
            return;
        }
        if (currentDirectMp4 != null && !currentDirectMp4.isEmpty()) {
            executeDirectHttpFileDownload(currentDirectMp4, fmtLabel, fmtExt);
        } else {
            Toast.makeText(this, "No direct media URL is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(java.util.Locale.US);
        return value.contains("youtube.com/") || value.contains("youtu.be/");
    }

    private boolean isSafeDirectMediaUrl(String value) {
        try {
            URL parsed = new URL(value);
            if (!"https".equalsIgnoreCase(parsed.getProtocol()) || parsed.getHost() == null || parsed.getHost().isEmpty()) return false;
            if (parsed.getUserInfo() != null) return false;
            String host = parsed.getHost().toLowerCase(java.util.Locale.US);
            if ("localhost".equals(host) || host.endsWith(".localhost") || host.equals("0.0.0.0") || host.equals("::1")) return false;
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private HttpURLConnection openValidatedConnection(String initialUrl) throws Exception {
        String nextUrl = initialUrl;
        for (int redirect = 0; redirect < 5; redirect++) {
            if (!isSafeDirectMediaUrl(nextUrl)) throw new java.io.IOException("Only safe HTTPS media URLs are supported");
            HttpURLConnection connection = (HttpURLConnection) new URL(nextUrl).openConnection();
            connection.setRequestProperty("User-Agent", "NEXORA/16 Android");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.connect();
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) throw new java.io.IOException("Redirect without destination");
                nextUrl = new URL(new URL(nextUrl), location).toString();
                continue;
            }
            return connection;
        }
        throw new java.io.IOException("Too many redirects");
    }

    private String inferMediaExtension(String contentType, String mediaUrl) {
        String mime = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(java.util.Locale.US);
        if ("video/mp4".equals(mime)) return ".mp4";
        if ("video/webm".equals(mime)) return ".webm";
        if ("video/x-matroska".equals(mime) || "video/mkv".equals(mime)) return ".mkv";
        if ("video/3gpp".equals(mime)) return ".3gp";
        if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime)) return ".mp3";
        if ("audio/mp4".equals(mime) || "audio/x-m4a".equals(mime)) return ".m4a";
        if ("audio/wav".equals(mime) || "audio/x-wav".equals(mime) || "audio/wave".equals(mime)) return ".wav";
        if ("audio/aac".equals(mime)) return ".aac";
        if ("audio/ogg".equals(mime)) return ".ogg";
        try {
            String path = new URL(mediaUrl).getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String ext = path.substring(dot).toLowerCase(java.util.Locale.US);
                if (".mp4".equals(ext) || ".webm".equals(ext) || ".mkv".equals(ext)
                        || ".3gp".equals(ext) || ".mp3".equals(ext) || ".m4a".equals(ext)
                        || ".wav".equals(ext) || ".aac".equals(ext) || ".ogg".equals(ext)) return ext;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String mimeForMediaExtension(String extension) {
        if (".mp4".equals(extension)) return "video/mp4";
        if (".webm".equals(extension)) return "video/webm";
        if (".mkv".equals(extension)) return "video/x-matroska";
        if (".3gp".equals(extension)) return "video/3gpp";
        if (".mp3".equals(extension)) return "audio/mpeg";
        if (".m4a".equals(extension)) return "audio/mp4";
        if (".wav".equals(extension)) return "audio/wav";
        if (".aac".equals(extension)) return "audio/aac";
        if (".ogg".equals(extension)) return "audio/ogg";
        throw new IllegalArgumentException("Unsupported media extension: " + extension);
    }

    private void executeDirectHttpFileDownload(final String streamUrl, final String fmtLabel, final String fmtExt) {
        Toast.makeText(this, "⬇ Saving original media unchanged...", Toast.LENGTH_SHORT).show();
        tvQualityBadge.setText("DL");
        tvActiveSub.setText("Connecting to the media source...");
        tvActiveDownloadStatus.setText("Preparing original source download...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                File partialFile = null;
                Uri pendingUri = null;
                HttpURLConnection conn = null;
                OutputStream output = null;
                try {
                    conn = openValidatedConnection(streamUrl);
                    int responseCode = conn.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        throw new java.io.IOException("Server returned HTTP " + responseCode);
                    }
                    String contentType = conn.getContentType();
                    if (contentType != null && contentType.toLowerCase(java.util.Locale.US).startsWith("text/html")) {
                        throw new java.io.IOException("URL returned a web page, not media");
                    }
                    final String actualExt = inferMediaExtension(contentType, streamUrl);
                    if (actualExt == null) {
                        throw new java.io.IOException("The source format is unknown. Nothing was saved with a guessed extension.");
                    }
                    final String actualMime = mimeForMediaExtension(actualExt);
                    String cleanTitle = (currentTitle == null || currentTitle.isEmpty() ? "NEXORA_Media" : currentTitle).replaceAll("[^a-zA-Z0-9_-]", "_");
                    if (cleanTitle.length() > 30) cleanTitle = cleanTitle.substring(0, 30);
                    final String fileName = cleanTitle + actualExt;

                    int totalBytes = conn.getContentLength();
                    InputStream is = conn.getInputStream();
                    final boolean modernStorage = Build.VERSION.SDK_INT >= 29;
                    final File nexoraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA");
                    final File outFile;
                    if (modernStorage) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                        values.put(MediaStore.Downloads.MIME_TYPE, actualMime);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NEXORA");
                        values.put(MediaStore.Downloads.IS_PENDING, 1);
                        pendingUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (pendingUri == null) throw new java.io.IOException("Could not create gallery download");
                        output = getContentResolver().openOutputStream(pendingUri);
                        if (output == null) throw new java.io.IOException("Could not open gallery output");
                        outFile = null;
                    } else {
                        if (!nexoraDir.exists() && !nexoraDir.mkdirs()) throw new java.io.IOException("Could not create Downloads/NEXORA");
                        outFile = new File(nexoraDir, fileName);
                        partialFile = new File(nexoraDir, fileName + ".part");
                        output = new FileOutputStream(partialFile);
                    }

                    byte[] buf = new byte[16384];
                    int read;
                    long downloaded = 0;
                    long lastUiUpdate = 0;
                    while ((read = is.read(buf)) != -1) {
                        output.write(buf, 0, read);
                        downloaded += read;
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate > 250) {
                            lastUiUpdate = now;
                            final int pct = (totalBytes > 0) ? (int) ((downloaded * 100L) / totalBytes) : -1;
                            final String kbStr = (downloaded / 1024) + " KB";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!canUpdateUi()) return;
                                    tvQualityBadge.setText(pct >= 0 ? ("DL " + pct + "%") : ("DL " + kbStr));
                                    tvActiveSub.setText("Downloading original " + fileName + " (" + kbStr + ")");
                                    tvActiveDownloadStatus.setText("Active: " + kbStr + (pct >= 0 ? (" • " + pct + "%") : ""));
                                }
                            });
                        }
                    }
                    output.flush();
                    output.close();
                    output = null;
                    is.close();
                    conn.disconnect();
                    conn = null;
                    if (modernStorage) {
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(pendingUri, ready, null, null);
                    } else if (!partialFile.renameTo(outFile)) {
                        throw new java.io.IOException("Could not finalize downloaded file");
                    }

                    final String savedLocation = modernStorage ? "Downloads/NEXORA/" + fileName : outFile.getAbsolutePath();
                    if (!modernStorage) {
                        MediaScannerConnection.scanFile(DashboardActivity.this, new String[]{outFile.getAbsolutePath()}, new String[]{actualMime}, null);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!canUpdateUi()) return;
                            tvQualityBadge.setText("SAVED ✓");
                            tvActiveSub.setText("Saved original source: " + savedLocation);
                            tvActiveDownloadStatus.setText("Completed: " + fileName);
                            Toast.makeText(DashboardActivity.this, "✅ Saved original media: " + fileName, Toast.LENGTH_LONG).show();
                            if (pageDownloads.getVisibility() == View.VISIBLE) refreshDownloadsManagerPage();
                            if (pageGallery.getVisibility() == View.VISIBLE) scanAndPopulatePhoneGalleryPage();
                        }
                    });
                } catch (final Exception e) {
                    try { if (output != null) output.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                    if (pendingUri != null) getContentResolver().delete(pendingUri, null, null);
                    if (partialFile != null && partialFile.exists()) partialFile.delete();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!canUpdateUi()) return;
                            tvQualityBadge.setText("DL ERROR");
                            tvActiveSub.setText("Download error: " + e.getMessage());
                            tvActiveDownloadStatus.setText("Download failed; no converted file was created.");
                        }
                    });
                }
            }
        }).start();
    }

    // =========================================================
    // 8. PAGE 2: DOWNLOADS MANAGER PAGE (ONLY REAL DOWNLOADED FILES!)
    // =========================================================
    private void refreshDownloadsManagerPage() {
        downloadsListContainer.removeAllViews();
        if (Build.VERSION.SDK_INT >= 29) {
            int count = 0;
            Cursor cursor = null;
            try {
                String[] projection = {
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.RELATIVE_PATH
                };
                cursor = getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Downloads.RELATIVE_PATH + "=? AND " + MediaStore.Downloads.IS_PENDING + "=0",
                    new String[]{Environment.DIRECTORY_DOWNLOADS + "/NEXORA/"},
                    MediaStore.Downloads.DISPLAY_NAME + " ASC"
                );
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                    int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
                    int mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE);
                    int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idIndex);
                        String name = cursor.getString(nameIndex);
                        String mime = cursor.getString(mimeIndex);
                        long size = cursor.getLong(sizeIndex);
                        Uri itemUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                        addLocalMediaRowToContainer(downloadsListContainer, itemUri, name, "NEXORA Download", size, mime);
                        count++;
                    }
                }
            } catch (Exception error) {
                TextView problem = new TextView(this);
                problem.setText("Could not read the NEXORA downloads index: " + error.getMessage());
                problem.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
                problem.setPadding(dp(12), dp(24), dp(12), dp(24));
                downloadsListContainer.addView(problem);
            } finally {
                if (cursor != null) cursor.close();
            }
            if (count == 0 && downloadsListContainer.getChildCount() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No NEXORA downloads were found in Downloads/NEXORA yet.");
                empty.setTextColor(isDarkMode ? Color.parseColor("#90A4AE") : Color.parseColor("#455A64"));
                empty.setTextSize(11.5f);
                empty.setPadding(dp(12), dp(24), dp(12), dp(24));
                downloadsListContainer.addView(empty);
            }
            return;
        }

        File nexoraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA");
        File[] files = nexoraDir.exists() ? nexoraDir.listFiles() : null;
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No files downloaded inside /Download/NEXORA yet.");
            empty.setTextColor(isDarkMode ? Color.parseColor("#90A4AE") : Color.parseColor("#455A64"));
            empty.setTextSize(11.5f);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            downloadsListContainer.addView(empty);
            return;
        }
        for (File file : files) {
            if (file.isDirectory() || file.length() < 500) continue;
            addLocalVideoRowToContainer(downloadsListContainer, file, "NEXORA Download");
        }
    }

    // =========================================================
    // 9. PAGE 3: MX PLAYER / PHONE GALLERY PAGE (SCANS ALL PHONE VIDEOS!)
    // =========================================================
    private void scanAndPopulatePhoneGalleryPage() {
        galleryListContainer.removeAllViews();
        if (!hasVideoReadPermission()) {
            TextView permissionMessage = new TextView(this);
            permissionMessage.setText("Allow video access to scan your phone gallery.");
            permissionMessage.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
            permissionMessage.setPadding(dp(12), dp(24), dp(12), dp(24));
            galleryListContainer.addView(permissionMessage);
            requestAppPermissions();
            return;
        }

        // Android 10+ must be browsed through MediaStore content URIs, not raw DATA paths.
        if (Build.VERSION.SDK_INT >= 29) {
            int count = 0;
            Cursor cursor = null;
            try {
                String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE
                };
                cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                );
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                    int bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idIndex);
                        String name = cursor.getString(nameIndex);
                        String bucket = cursor.getString(bucketIndex);
                        long size = cursor.getLong(sizeIndex);
                        Uri itemUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        addLocalMediaRowToContainer(galleryListContainer, itemUri, name,
                                bucket == null ? "Phone Storage" : bucket, size, "video/*");
                        count++;
                    }
                }
            } catch (Exception error) {
                TextView problem = new TextView(this);
                problem.setText("Could not scan MediaStore: " + error.getMessage());
                problem.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
                problem.setPadding(dp(12), dp(24), dp(12), dp(24));
                galleryListContainer.addView(problem);
            } finally {
                if (cursor != null) cursor.close();
            }
            tvGalleryHeader.setText("🎬 Phone Gallery (" + count + " Videos)");
            if (count == 0 && galleryListContainer.getChildCount() == 0) {
                TextView empty = new TextView(this);
                empty.setText("No videos are currently visible in the phone media library.");
                empty.setTextColor(isDarkMode ? Color.parseColor("#90A4AE") : Color.parseColor("#455A64"));
                empty.setTextSize(11.5f);
                empty.setPadding(dp(12), dp(24), dp(12), dp(24));
                galleryListContainer.addView(empty);
            }
            return;
        }

        // Pre-Android 10 compatibility path.
        final ArrayList<File> allPhoneVideos = new ArrayList<>();
        final ArrayList<String> folderNames = new ArrayList<>();
        try {
            String[] projection = {
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.SIZE
            };
            Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            );
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    String bucket = cursor.getString(bucketIdx);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists() && file.length() > 1000 && !allPhoneVideos.contains(file)) {
                            allPhoneVideos.add(file);
                            folderNames.add(bucket != null ? bucket : "Phone Storage");
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        File extRoot = Environment.getExternalStorageDirectory();
        File[] commonDirs = {
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            new File(extRoot, "WhatsApp/Media/WhatsApp Video")
        };
        for (File dir : commonDirs) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] entries = dir.listFiles();
                if (entries != null) {
                    for (File file : entries) {
                        String name = file.getName().toLowerCase(java.util.Locale.US);
                        if (!file.isDirectory() && (name.endsWith(".mp4") || name.endsWith(".mkv")
                                || name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".mp3")
                                || name.endsWith(".m4a") || name.endsWith(".wav")) && file.length() > 1000) {
                            if (!allPhoneVideos.contains(file)) {
                                allPhoneVideos.add(file);
                                folderNames.add(dir.getName());
                            }
                        }
                    }
                }
            }
        }
        tvGalleryHeader.setText("🎬 Phone Gallery (" + allPhoneVideos.size() + " Real Videos)");
        if (allPhoneVideos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No local videos found on device storage.");
            empty.setTextColor(isDarkMode ? Color.parseColor("#90A4AE") : Color.parseColor("#455A64"));
            empty.setTextSize(11.5f);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            galleryListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < allPhoneVideos.size(); i++) {
            addLocalVideoRowToContainer(galleryListContainer, allPhoneVideos.get(i), folderNames.get(i));
        }
    }

    private void addLocalMediaRowToContainer(LinearLayout parent, final Uri mediaUri,
            final String displayName, String folderLabel, final long sizeBytes, final String mimeType) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_card);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);

        final ImageView thumbnail = new ImageView(this);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(84), dp(54));
        thumbParams.rightMargin = dp(10);
        thumbnail.setLayoutParams(thumbParams);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundColor(Color.parseColor("#0B1324"));
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final Bitmap bitmap = getContentResolver().loadThumbnail(mediaUri, new Size(dp(84), dp(54)), null);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (canUpdateUi() && bitmap != null) thumbnail.setImageBitmap(bitmap);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }).start();

        LinearLayout info = new LinearLayout(this);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(displayName == null ? "Media file" : displayName);
        name.setTextSize(10.5f);
        name.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        long sizeKb = sizeBytes / 1024;
        String sizeText = sizeKb >= 1024 ? String.format("%.1f MB", sizeKb / 1024f) : sizeKb + " KB";
        TextView meta = new TextView(this);
        meta.setText("📁 " + folderLabel + " • " + sizeText + (mimeType == null ? "" : " • " + mimeType));
        meta.setTextSize(8.5f);
        meta.setTextColor(Color.parseColor("#00E5FF"));
        info.addView(name);
        info.addView(meta);

        TextView play = new TextView(this);
        play.setBackgroundResource(R.drawable.chip_active);
        play.setPadding(dp(10), dp(6), dp(10), dp(6));
        play.setText("▶ Play");
        play.setTextColor(Color.WHITE);
        play.setTextSize(9.5f);
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                switchPage(0);
                currentTitle = displayName == null ? "Local media" : displayName;
                currentVideoUrl = mediaUri.toString();
                currentDirectMp4 = mediaUri.toString();
                tvActiveTitle.setText(currentTitle);
                tvActiveSub.setText("Playing local media");
                playDirectMp4InNativeView(mediaUri.toString());
            }
        });

        TextView delete = new TextView(this);
        delete.setPadding(dp(8), dp(6), dp(4), dp(6));
        delete.setText("🗑️");
        delete.setTextSize(12f);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    int deleted = getContentResolver().delete(mediaUri, null, null);
                    if (deleted > 0) {
                        Toast.makeText(DashboardActivity.this, "Deleted: " + displayName, Toast.LENGTH_SHORT).show();
                        if (pageDownloads.getVisibility() == View.VISIBLE) refreshDownloadsManagerPage();
                        if (pageGallery.getVisibility() == View.VISIBLE) scanAndPopulatePhoneGalleryPage();
                    } else {
                        Toast.makeText(DashboardActivity.this, "Android did not delete this media item.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception error) {
                    Toast.makeText(DashboardActivity.this, "Delete failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        card.addView(thumbnail);
        card.addView(info);
        card.addView(play);
        card.addView(delete);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { play.performClick(); }
        });
        parent.addView(card);
    }

    private void addLocalVideoRowToContainer(LinearLayout parent, final File videoFile, String folderLabel) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_card);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        // Real Video Thumbnail Box (MX Player style)
        final ImageView ivThumb = new ImageView(this);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(84), dp(54));
        thumbLp.rightMargin = dp(10);
        ivThumb.setLayoutParams(thumbLp);
        ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivThumb.setBackgroundColor(Color.parseColor("#0B1324"));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                    if (thumb != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ivThumb.setImageBitmap(thumb);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();

        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        infoBox.setOrientation(LinearLayout.VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(videoFile.getName());
        tvName.setTextSize(10.5f);
        tvName.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
        tvName.setMaxLines(2);
        tvName.setEllipsize(TextUtils.TruncateAt.END);

        long sizeKb = videoFile.length() / 1024;
        String sizeStr = sizeKb >= 1024 ? String.format("%.1f MB", sizeKb / 1024f) : (sizeKb + " KB");

        TextView tvMeta = new TextView(this);
        tvMeta.setText("📁 " + folderLabel + " • " + sizeStr);
        tvMeta.setTextSize(8.5f);
        tvMeta.setTextColor(Color.parseColor("#00E5FF"));

        infoBox.addView(tvName);
        infoBox.addView(tvMeta);

        TextView btnPlay = new TextView(this);
        btnPlay.setBackgroundResource(R.drawable.chip_active);
        btnPlay.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnPlay.setText("▶ Play");
        btnPlay.setTextColor(Color.WHITE);
        btnPlay.setTextSize(9.5f);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchPage(0);
                currentTitle = videoFile.getName();
                tvActiveTitle.setText(currentTitle);
                tvActiveSub.setText("Playing Local Video from Phone Gallery");
                playDirectMp4InNativeView(videoFile.getAbsolutePath());
            }
        });

        TextView btnDel = new TextView(this);
        btnDel.setPadding(dp(8), dp(6), dp(4), dp(6));
        btnDel.setText("🗑️");
        btnDel.setTextSize(12f);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoFile.delete()) {
                    Toast.makeText(DashboardActivity.this, "Deleted: " + videoFile.getName(), Toast.LENGTH_SHORT).show();
                    if (pageDownloads.getVisibility() == View.VISIBLE) refreshDownloadsManagerPage();
                    if (pageGallery.getVisibility() == View.VISIBLE) scanAndPopulatePhoneGalleryPage();
                }
            }
        });

        card.addView(ivThumb);
        card.addView(infoBox);
        card.addView(btnPlay);
        card.addView(btnDel);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchPage(0);
                currentTitle = videoFile.getName();
                tvActiveTitle.setText(currentTitle);
                tvActiveSub.setText("Playing Local Video from Phone Gallery");
                playDirectMp4InNativeView(videoFile.getAbsolutePath());
            }
        });

        parent.addView(card);
    }

    // =========================================================
    // 10. PAGE 4: DEDICATED FULL-SCREEN SETTINGS PAGE
    // =========================================================
    private void buildFullSettingsPage() {
        settingsContentContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setText("⚙️ NEXORA Pro Settings");
        header.setTextSize(15f);
        header.setTextColor(isDarkMode ? Color.parseColor("#00E5FF") : Color.parseColor("#0066CC"));
        header.setPadding(dp(4), dp(4), dp(4), dp(12));
        settingsContentContainer.addView(header);

        // 1. Dark Mode / Light Mode Switch
        addSettingSwitchRow(
            "🌙 Dark Mode / ☀️ Light Mode",
            "Syncs automatically with phone theme, or toggle manually",
            isDarkMode,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Switch sw = (Switch) v;
                    forceThemeOverride = true;
                    isDarkMode = sw.isChecked();
                    applyThemeColors();
                    buildFullSettingsPage();
                }
            }
        );

        // 2. Auto-Play Next Video Switch
        addSettingSwitchRow(
            "▶ Auto-Play Next Video in Loop",
            "Automatically plays the next video when current finishes",
            autoPlayNext,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    autoPlayNext = ((Switch) v).isChecked();
                    prefs.edit().putBoolean("autoplay_next", autoPlayNext).apply();
                }
            }
        );

        // The standalone C++ helper is not wired into this Activity's player.
        TextView decoderInfo = new TextView(this);
        decoderInfo.setText("Playback uses Android VideoView/MediaPlayer. The separate C++ helper is not integrated, so a custom GPU decoder switch is not available in this build.");
        decoderInfo.setTextColor(isDarkMode ? Color.parseColor("#80B3FF") : Color.parseColor("#455A64"));
        decoderInfo.setTextSize(10f);
        decoderInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        settingsContentContainer.addView(decoderInfo);

        // 4. Default Format & Quality Selector Row
        LinearLayout fmtRow = new LinearLayout(this);
        fmtRow.setOrientation(LinearLayout.HORIZONTAL);
        fmtRow.setGravity(Gravity.CENTER_VERTICAL);
        fmtRow.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_card);
        fmtRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.bottomMargin = dp(8);
        fmtRow.setLayoutParams(flp);

        TextView tvFmt = new TextView(this);
        tvFmt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvFmt.setText("📥 Default Download Format\nCurrent: " + selectedFormatLabel + " (" + selectedFormatExt + ")");
        tvFmt.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
        tvFmt.setTextSize(11f);

        TextView btnPickFmt = new TextView(this);
        btnPickFmt.setBackgroundResource(R.drawable.chip_active);
        btnPickFmt.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnPickFmt.setText("Change ⋮");
        btnPickFmt.setTextColor(Color.WHITE);
        btnPickFmt.setTextSize(10f);
        btnPickFmt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThreeDotFormatSelectorDialog();
            }
        });
        fmtRow.addView(tvFmt);
        fmtRow.addView(btnPickFmt);
        settingsContentContainer.addView(fmtRow);

        // 5. Storage Location Info Card
        LinearLayout storageCard = new LinearLayout(this);
        storageCard.setOrientation(LinearLayout.VERTICAL);
        storageCard.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_card);
        storageCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        storageCard.setLayoutParams(flp);

        TextView stText = new TextView(this);
        stText.setText("📁 Download Storage Path:\n/storage/emulated/0/Download/NEXORA/\n\nAll downloaded videos & MP3s are automatically indexed into your Phone Gallery.");
        stText.setTextColor(isDarkMode ? Color.parseColor("#B0BEC5") : Color.parseColor("#37474F"));
        stText.setTextSize(10.5f);
        storageCard.addView(stText);
        settingsContentContainer.addView(storageCard);
    }

    private void addSettingSwitchRow(String title, String subtitle, boolean checked, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_card);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(11.5f);
        t.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(9f);
        s.setTextColor(isDarkMode ? Color.parseColor("#80B3FF") : Color.parseColor("#557799"));

        textCol.addView(t);
        textCol.addView(s);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnClickListener(listener);

        row.addView(textCol);
        row.addView(sw);
        settingsContentContainer.addView(row);
    }

    // =========================================================
    // 11. ALL CONTROLS & LISTENERS
    // =========================================================
    private void setupAllControls() {
        final View btnMix = findViewById(R.id.btnAllInOneHub);
        final View tabYt = findViewById(R.id.tabYoutube);
        final View tabIg = findViewById(R.id.tabInsta);
        final View tabTk = findViewById(R.id.tabTiktok);
        final View tabX = findViewById(R.id.tabX);

        View.OnClickListener platformClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int inactiveBg = isDarkMode ? R.drawable.frame_slate_pill : R.drawable.frame_light_pill;
                btnMix.setBackgroundResource(inactiveBg);
                tabYt.setBackgroundResource(inactiveBg);
                tabIg.setBackgroundResource(inactiveBg);
                tabTk.setBackgroundResource(inactiveBg);
                tabX.setBackgroundResource(inactiveBg);
                v.setBackgroundResource(R.drawable.frame_neon_red);

                if (v == btnMix) {
                    currentPlatform = "ALL";
                    tvPlatformBadge.setText("MIX");
                    loadUnifiedMixedFeed("Trending Viral Songs & Videos 2026");
                } else if (v == tabYt) {
                    currentPlatform = "YOUTUBE";
                    tvPlatformBadge.setText("YT");
                    loadUnifiedMixedFeed("Bollywood New Songs 2026");
                } else if (v == tabTk) {
                    currentPlatform = "TIKTOK";
                    tvPlatformBadge.setText("TK");
                    loadUnifiedMixedFeed("tiktok viral dance trend");
                } else if (v == tabIg) {
                    currentPlatform = "INSTAGRAM";
                    tvPlatformBadge.setText("IG");
                    loadUnifiedMixedFeed("instagram viral reels");
                } else if (v == tabX) {
                    currentPlatform = "X";
                    tvPlatformBadge.setText("𝕏");
                    loadUnifiedMixedFeed("viral news clips");
                }
            }
        };
        btnMix.setOnClickListener(platformClick);
        tabYt.setOnClickListener(platformClick);
        tabTk.setOnClickListener(platformClick);
        tabIg.setOnClickListener(platformClick);
        tabX.setOnClickListener(platformClick);

        // Upper URL Bar
        findViewById(R.id.btnClearUrl).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etUrl.setText(""); }
        });
        findViewById(R.id.btnFetchVideo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String link = etUrl.getText().toString().trim();
                if (link.isEmpty()) {
                    Toast.makeText(DashboardActivity.this, "Paste a video link first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentVideoUrl = link;
                String ytId = extractYouTubeId(link);
                if (!ytId.isEmpty()) {
                    currentVideoId = ytId;
                    currentTitle = "YouTube Video (" + ytId + ")";
                    tvActiveTitle.setText(currentTitle);
                    currentDirectMp4 = "";
                    openOfficialYouTube(link);
                } else {
                    currentDirectMp4 = link;
                    currentTitle = "Direct Link Video";
                    tvActiveTitle.setText(currentTitle);
                    playDirectMp4InNativeView(link);
                }
            }
        });

        // Lower Search Bar
        findViewById(R.id.btnClearSearch).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etVideoSearch.setText(""); }
        });
        findViewById(R.id.btnSearchVideos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = etVideoSearch.getText().toString().trim();
                if (q.isEmpty()) {
                    Toast.makeText(DashboardActivity.this, "Enter a keyword to search!", Toast.LENGTH_SHORT).show();
                    return;
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etVideoSearch.getWindowToken(), 0);
                loadUnifiedMixedFeed(q);
            }
        });

        // Player Controls
        View.OnClickListener playToggle = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (realVideoView.isPlaying()) {
                    realVideoView.pause();
                    tvQualityBadge.setText("PAUSED");
                    btnCenterPlay.setVisibility(View.VISIBLE);
                } else if (isNativeVideoPrepared) {
                    realVideoView.start();
                    tvQualityBadge.setText("PLAYING HD");
                    btnCenterPlay.setVisibility(View.GONE);
                } else {
                    onPoolCardTapped(selectedPoolIndex);
                }
            }
        };
        btnCenterPlay.setOnClickListener(playToggle);
        findViewById(R.id.actPlay).setOnClickListener(playToggle);

        // Download Button & 3-Dot (⋮) Format Selector
        findViewById(R.id.actDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadCurrentSelectedVideo(selectedFormatLabel, selectedFormatExt);
            }
        });
        findViewById(R.id.btnDownloadThreeDots).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThreeDotFormatSelectorDialog();
            }
        });

        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isNativeVideoPrepared) {
                    realVideoView.seekTo(progress);
                    tvCurrentTime.setText(formatMs(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btnMediaPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int prev = (selectedPoolIndex - 1 + POOL_SIZE) % POOL_SIZE;
                onPoolCardTapped(prev);
            }
        });
        findViewById(R.id.btnMediaNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int next = (selectedPoolIndex + 1) % POOL_SIZE;
                onPoolCardTapped(next);
            }
        });
        btnSpeedControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSpeed == 1.0f) currentSpeed = 1.25f;
                else if (currentSpeed == 1.25f) currentSpeed = 1.5f;
                else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
                else currentSpeed = 1.0f;
                btnSpeedControl.setText(currentSpeed + "x");
                Toast.makeText(DashboardActivity.this, "Speed: " + currentSpeed + "x", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.actShare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String link = currentVideoUrl.isEmpty() ? "https://www.youtube.com" : currentVideoUrl;
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, link);
                startActivity(Intent.createChooser(share, "Share Video"));
            }
        });

        findViewById(R.id.actPip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                }
            }
        });

        findViewById(R.id.btnFullscreen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPlayerFullscreen(!isPlayerFullscreen);
            }
        });

        // Category Chips
        final int[] chipIds = {R.id.chipRec, R.id.chipTrend, R.id.chipMusic, R.id.chipGaming, R.id.chipNews, R.id.chipShorts};
        final String[] chipQueries = {
            "Trending Viral Songs 2026",
            "Trending India Viral Videos",
            "New Hindi Hit Songs 2026",
            "GTA 6 & PS5 Gameplay 4K",
            "Breaking News Live Clips",
            "Viral Reels & TikTok Shorts"
        };

        for (int i = 0; i < chipIds.length; i++) {
            final int idx = i;
            findViewById(chipIds[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int id : chipIds) {
                        findViewById(id).setBackgroundResource(R.drawable.chip_inactive);
                    }
                    v.setBackgroundResource(R.drawable.chip_active);
                    loadUnifiedMixedFeed(chipQueries[idx]);
                }
            });
        }

        findViewById(R.id.btnRescanGallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanAndPopulatePhoneGalleryPage();
            }
        });

        // Bottom Navigation Bar (Switches between separate full-screen pages!)
        findViewById(R.id.navHome).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(0); }
        });
        findViewById(R.id.navSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchPage(0);
                pageHome.smoothScrollTo(0, 0);
                etVideoSearch.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etVideoSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        findViewById(R.id.navDownloads).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(1); }
        });
        findViewById(R.id.navMyVideos).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(2); }
        });
        findViewById(R.id.navLibrary).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchPage(3); }
        });
    }

    private boolean canUpdateUi() {
        return !activityDestroyed && !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private boolean hasVideoReadPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasVideoReadPermission()) return;
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 101) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pageGallery != null && pageGallery.getVisibility() == View.VISIBLE) {
                scanAndPopulatePhoneGalleryPage();
            }
        } else if (pageGallery != null && pageGallery.getVisibility() == View.VISIBLE) {
            galleryListContainer.removeAllViews();
            TextView message = new TextView(this);
            message.setText("Video access was not granted. Allow media access in Android Settings to browse your videos.");
            message.setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
            message.setPadding(dp(12), dp(24), dp(12), dp(24));
            galleryListContainer.addView(message);
        }
    }

    private void setPlayerFullscreen(boolean fullscreen) {
        if (fullscreen == isPlayerFullscreen || fullscreenHost == null || playerContainerBox == null) return;

        if (fullscreen) {
            ViewGroup parent = (ViewGroup) playerContainerBox.getParent();
            if (parent == null) return;
            playerOriginalParent = parent;
            playerOriginalIndex = parent.indexOfChild(playerContainerBox);
            playerOriginalLayoutParams = playerContainerBox.getLayoutParams();
            parent.removeView(playerContainerBox);

            fullscreenHost.addView(playerContainerBox, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            fullscreenHost.setVisibility(View.VISIBLE);
            fullscreenHost.bringToFront();
            isPlayerFullscreen = true;
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            if (playerContainerBox.getParent() == fullscreenHost) {
                fullscreenHost.removeView(playerContainerBox);
            }
            if (playerOriginalParent != null) {
                int index = Math.max(0, Math.min(playerOriginalIndex, playerOriginalParent.getChildCount()));
                playerOriginalParent.addView(playerContainerBox, index, playerOriginalLayoutParams);
            }
            fullscreenHost.setVisibility(View.GONE);
            isPlayerFullscreen = false;
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (isPlayerFullscreen) {
            setPlayerFullscreen(false);
            return;
        }
        super.onBackPressed();
    }

    private String extractYouTubeId(String url) {
        Pattern p = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})");
        Matcher m = p.matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        feedRequestSequence++;
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
        if (realVideoView != null) realVideoView.stopPlayback();
    }
}
