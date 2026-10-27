package com.lion.downloader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // चेक करें कि यूजर ने पहले टिक करके लॉगिन/स्टार्ट किया है या नहीं
        SharedPreferences prefs = getSharedPreferences("nexora_permanent_auth", MODE_PRIVATE);
        boolean isAccepted = prefs.getBoolean("boxes_ticked_and_started", false);

        if (isAccepted) {
            // अगर एक बार टिक हो चुका है, तो सीधे DashboardActivity खोलें (जब तक ऐप रीसेट न हो)
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        // पहली बार खुलने पर पॉलिसी/स्टार्ट पेज पर भेजें
        startActivity(new Intent(this, PolicyActivity.class));
        finish();
    }
}
