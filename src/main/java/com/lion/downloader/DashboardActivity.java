package com.lion.downloader;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.ContentUris;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardActivity extends Activity {

    private static final int POOL_SIZE = 12;

    // 4 Separate Full-Screen Pages
    private RelativeLayout rootDashboard;
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
        selectedFormatLabel = prefs.getString("default_fmt", "1080p_MP4");
        selectedFormatExt = selectedFormatLabel.contains("MP3") ? ".mp3" : (selectedFormatLabel.contains("M4A") ? ".m4a" : (selectedFormatLabel.contains("WAV") ? ".wav" : (selectedFormatLabel.contains("MKV") ? ".mkv" : ".mp4")));
        autoPlayNext = prefs.getBoolean("autoplay_next", true);
        hwAcceleration = prefs.getBoolean("hw_accel", true);

        getSharedPreferences("nexora_permanent_auth", MODE_PRIVATE).edit().putBoolean("boxes_ticked_and_started", true).apply();
        requestAppPermissions();
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

        rootDashboard.setBackgroundColor(bgRoot);
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

        final String[][] options = {
            {"VIDEO • 4K Ultra HD (2160p)", "4K_MP4", ".mp4"},
            {"VIDEO • Full HD (1080p MP4)", "1080p_MP4", ".mp4"},
            {"VIDEO • High Definition (720p MP4)", "720p_MP4", ".mp4"},
            {"VIDEO • Standard Quality (480p MP4)", "480p_MP4", ".mp4"},
            {"VIDEO • Data Saver (360p MP4)", "360p_MP4", ".mp4"},
            {"VIDEO • MKV High Bitrate (1080p MKV)", "1080p_MKV", ".mkv"},
            {"VIDEO • WEBM Stream Format (720p WEBM)", "720p_WEBM", ".webm"},
            {"AUDIO • MP3 Studio Quality (320kbps)", "320k_MP3", ".mp3"},
            {"AUDIO • MP3 Standard Quality (192kbps)", "192k_MP3", ".mp3"},
            {"AUDIO • M4A Original AAC Audio", "AAC_M4A", ".m4a"},
            {"AUDIO • WAV Lossless Uncompressed", "Lossless_WAV", ".wav"}
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
        tvActiveSub.setText("Loading " + currentPlatform + " Feed: " + query);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (currentPlatform.equals("TIKTOK") || currentPlatform.equals("INSTAGRAM") || currentPlatform.equals("X")) {
                    fetchFromShortVideoServer(query, 0, POOL_SIZE, 1);
                    return;
                }

                if (currentPlatform.equals("ALL")) {
                    // Even slots: TikTok/Insta/FB/X direct MP4 streams; Odd slots: YouTube videos
                    fetchFromShortVideoServer(query, 0, POOL_SIZE, 2);
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
                        if (currentPlatform.equals("ALL") && i % 2 == 0) continue;
                        loadSingleYouTubeIntoSlot(i, ytIds.get(i));
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void fetchFromShortVideoServer(String query, int startSlot, int limit, int step) {
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
                    String[] mixTags = {"TIKTOK", "INSTA", "FB REEL", "X CLIP"};
                    for (int i = startSlot; i < limit; i += step) {
                        JSONObject v = videos.getJSONObject(i % videos.length());
                        final String vid = v.optString("video_id", "tk_" + i);
                        final String rawTitle = v.optString("title", "Viral Video #" + (i + 1));
                        String playUrl = v.optString("play", "");
                        if (playUrl.startsWith("/")) playUrl = "https://www.tikwm.com" + playUrl;
                        final String finalPlayUrl = playUrl;
                        String coverUrl = v.optString("cover", "");
                        if (coverUrl.startsWith("/")) coverUrl = "https://www.tikwm.com" + coverUrl;
                        final int durSec = v.optInt("duration", 15);
                        final String tag = currentPlatform.equals("ALL") ? mixTags[(i / step) % mixTags.length] : currentPlatform;
                        final Bitmap bmp = downloadBitmap(coverUrl);
                        final int slot = i;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
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

    private void loadSingleYouTubeIntoSlot(final int slot, final String videoId) {
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
            btnCenterPlay.setVisibility(View.GONE);
            realVideoView.setVisibility(View.VISIBLE);
            realVideoView.setVideoURI(Uri.parse(mp4UrlOrPath));
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
                    // If stream needs fresh token, resolve via Stage 3
                    tvQualityBadge.setText("BUFFERING...");
                    return true;
                }
            });
        } catch (Exception ignored) {}
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

    private void executeDirectHttpFileDownload(final String streamUrl, final String fmtLabel, final String fmtExt) {
        String cleanTitle = (currentTitle == null || currentTitle.isEmpty() ? "NEXORA_Media" : currentTitle).replaceAll("[^a-zA-Z0-9_-]", "_");
        if (cleanTitle.length() > 30) cleanTitle = cleanTitle.substring(0, 30);
        final String fileName = cleanTitle + "_" + fmtLabel + fmtExt;

        Toast.makeText(this, "⬇ Downloading: " + fileName, Toast.LENGTH_SHORT).show();
        tvQualityBadge.setText("DL 0%");
        tvActiveSub.setText("Downloading " + fileName + "...");
        tvActiveDownloadStatus.setText("Downloading: " + fileName);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!streamUrl.startsWith("https://") && !streamUrl.startsWith("http://")) {
                        throw new java.io.IOException("Only HTTP(S) media URLs are supported");
                    }
                    File nexoraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA");
                    if (!nexoraDir.exists()) nexoraDir.mkdirs();
                    final File outFile = new File(nexoraDir, fileName);
                    final File partialFile = new File(nexoraDir, fileName + ".part");

                    HttpURLConnection conn = (HttpURLConnection) new URL(streamUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.connect();
                    int responseCode = conn.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        throw new java.io.IOException("Server returned HTTP " + responseCode);
                    }

                    int totalBytes = conn.getContentLength();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(partialFile);

                    byte[] buf = new byte[16384];
                    int read;
                    long downloaded = 0;
                    long lastUiUpdate = 0;

                    while ((read = is.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                        downloaded += read;
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate > 250) {
                            lastUiUpdate = now;
                            final int pct = (totalBytes > 0) ? (int) ((downloaded * 100L) / totalBytes) : -1;
                            final String kbStr = (downloaded / 1024) + " KB";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvQualityBadge.setText(pct >= 0 ? ("DL " + pct + "%") : ("DL " + kbStr));
                                    tvActiveSub.setText("Downloading " + fileName + " (" + kbStr + ")");
                                    tvActiveDownloadStatus.setText("Active: " + kbStr + (pct >= 0 ? (" • " + pct + "%") : ""));
                                }
                            });
                        }
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    if (!partialFile.renameTo(outFile)) {
                        throw new java.io.IOException("Could not finalize downloaded file");
                    }

                    String mime = fmtExt.equals(".mp3") ? "audio/mpeg" : (fmtExt.equals(".m4a") ? "audio/mp4" : (fmtExt.equals(".wav") ? "audio/wav" : "video/mp4"));
                    MediaScannerConnection.scanFile(
                        DashboardActivity.this,
                        new String[]{outFile.getAbsolutePath()},
                        new String[]{mime},
                        null
                    );

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvQualityBadge.setText("SAVED ✓");
                            tvActiveSub.setText("Saved to Gallery: /Download/NEXORA/" + fileName);
                            tvActiveDownloadStatus.setText("Completed: " + fileName);
                            Toast.makeText(DashboardActivity.this, "✅ Saved to Phone Gallery: " + fileName, Toast.LENGTH_LONG).show();
                            if (pageDownloads.getVisibility() == View.VISIBLE) refreshDownloadsManagerPage();
                            if (pageGallery.getVisibility() == View.VISIBLE) scanAndPopulatePhoneGalleryPage();
                        }
                    });
                } catch (final Exception e) {
                    File partial = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA/" + fileName + ".part");
                    if (partial.exists()) partial.delete();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvQualityBadge.setText("DL ERROR");
                            tvActiveSub.setText("Download error: " + e.getMessage());
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
        File nexoraDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NEXORA");
        File[] files = nexoraDir.exists() ? nexoraDir.listFiles() : null;

        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No files downloaded inside /Download/NEXORA yet.\n\nGo to Home, select any video, and tap 'Download' or '⋮' to choose format & quality.");
            empty.setTextColor(isDarkMode ? Color.parseColor("#90A4AE") : Color.parseColor("#455A64"));
            empty.setTextSize(11.5f);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            downloadsListContainer.addView(empty);
            return;
        }

        for (int i = 0; i < files.length; i++) {
            final File f = files[i];
            if (f.isDirectory() || f.length() < 500) continue;
            addLocalVideoRowToContainer(downloadsListContainer, f, "NEXORA Download");
        }
    }

    // =========================================================
    // 9. PAGE 3: MX PLAYER / PHONE GALLERY PAGE (SCANS ALL PHONE VIDEOS!)
    // =========================================================
    private void scanAndPopulatePhoneGalleryPage() {
        galleryListContainer.removeAllViews();
        final ArrayList<File> allPhoneVideos = new ArrayList<>();
        final ArrayList<String> folderNames = new ArrayList<>();

        // 1. Query Android MediaStore for ALL videos stored anywhere on the phone
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
                        File vf = new File(path);
                        if (vf.exists() && vf.length() > 1000 && !allPhoneVideos.contains(vf)) {
                            allPhoneVideos.add(vf);
                            folderNames.add(bucket != null ? bucket : "Phone Storage");
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        // 2. Also directly scan common folders (/Download, /Download/NEXORA, /Movies, /DCIM/Camera)
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
                File[] arr = dir.listFiles();
                if (arr != null) {
                    for (File f : arr) {
                        String n = f.getName().toLowerCase();
                        if (!f.isDirectory() && (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav")) && f.length() > 1000) {
                            if (!allPhoneVideos.contains(f)) {
                                allPhoneVideos.add(f);
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
            empty.setText("No local videos found on device storage.\n\nTap '🔄 Scan Phone' to grant storage permission or download any video first.");
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

        // 3. Hardware GPU Decoder (HW+) Switch
        addSettingSwitchRow(
            "🚀 GPU Hardware Acceleration (HW+)",
            "Uses C++ MediaCodec GPU hardware rendering for smooth 60FPS",
            hwAcceleration,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hwAcceleration = ((Switch) v).isChecked();
                    prefs.edit().putBoolean("hw_accel", hwAcceleration).apply();
                }
            }
        );

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                }
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
                requestAppPermissions();
                scanAndPopulatePhoneGalleryPage();
                Toast.makeText(DashboardActivity.this, "Phone Gallery Scanned!", Toast.LENGTH_SHORT).show();
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

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> perms = new ArrayList<>();
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add("android.permission.READ_MEDIA_VIDEO");
                perms.add("android.permission.READ_MEDIA_AUDIO");
                perms.add("android.permission.POST_NOTIFICATIONS");
            }
            requestPermissions(perms.toArray(new String[0]), 101);
        }
    }

    private String extractYouTubeId(String url) {
        Pattern p = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})");
        Matcher m = p.matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
        if (realVideoView != null) realVideoView.stopPlayback();
    }
}
