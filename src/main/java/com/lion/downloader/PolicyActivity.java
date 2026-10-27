package com.lion.downloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.graphics.Color;

public class PolicyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy);

        final CheckBox chkTerms = findViewById(R.id.chkTerms);
        final CheckBox chkPrivacy = findViewById(R.id.chkPrivacy);
        Button btnReadPolicy = findViewById(R.id.btnReadPolicy);
        final Button btnContinue = findViewById(R.id.btnContinue);

        CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (chkTerms.isChecked() && chkPrivacy.isChecked()) {
                    btnContinue.setEnabled(true);
                    btnContinue.setTextColor(Color.parseColor("#000000"));
                } else {
                    btnContinue.setEnabled(false);
                    btnContinue.setTextColor(Color.parseColor("#888888"));
                }
            }
        };

        chkTerms.setOnCheckedChangeListener(listener);
        chkPrivacy.setOnCheckedChangeListener(listener);

        btnReadPolicy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(PolicyActivity.this);
                builder.setTitle("Legal Agreements");
                builder.setMessage("1. Terms of Service:\nUsers must comply with all local laws and copyright regulations (DMCA). The app is for personal, non-commercial use only.\n\n2. Privacy Policy:\nWe do not store your downloaded files on our servers. Your device ID may be securely logged for abuse prevention strictly in accordance with GDPR guidelines.");
                builder.setPositiveButton("Understood", null);
                builder.show();
            }
        });

        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // पॉलिसी एक्सेप्ट करने के बाद GPU एनिमेशन वीडियो पेज पर जाएँ
                Intent intent = new Intent(PolicyActivity.this, AnimationActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}
