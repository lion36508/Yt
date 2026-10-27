package com.lion.downloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity {
    String[] languages = {"English", "中文 (Chinese)", "हिन्दी (Hindi)", "Español", "Français", "Deutsch"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView langSelector = findViewById(R.id.langSelector);
        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        
        langSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select Language");
                builder.setItems(languages, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selected = languages[which].split(" ")[0];
                        langSelector.setText("🌐 " + selected + " ﹀");
                    }
                });
                builder.show();
            }
        });

        btnGetStarted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PolicyActivity.class);
                startActivity(intent);
                // फेड एनिमेशन
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }
}
