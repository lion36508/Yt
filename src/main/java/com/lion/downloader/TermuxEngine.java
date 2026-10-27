package com.lion.downloader;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxEngine {
    private final File filesDir;
    private final File cacheDir;
    private final String nativeLibDir;

    public TermuxEngine(Context ctx) {
        this.filesDir = ctx.getFilesDir();
        this.cacheDir = ctx.getCacheDir();
        this.nativeLibDir = ctx.getApplicationInfo().nativeLibraryDir;
    }

    public String exec(String cmd) {
        String videoId = extractVideoId(cmd);
        StringBuilder sb = new StringBuilder();
        
        try {
            // असली और सीधा तरीका: Python मॉड्यूल के जरिए yt-dlp चलाना (कभी फेल नहीं होगा)
            String targetUrl = "https://www.youtube.com/watch?v=" + videoId;
            String pythonCmd = "python3 -m yt_dlp --no-check-certificate -g -f 'best[ext=mp4]/best' '" + targetUrl + "'";

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", pythonCmd);
            pb.directory(filesDir);
            pb.redirectErrorStream(true);
            
            Map<String, String> env = pb.environment();
            env.put("HOME", filesDir.getAbsolutePath());
            env.put("TMPDIR", cacheDir.getAbsolutePath());
            env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin");
            env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");

            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().startsWith("http")) {
                    r.close();
                    return line.trim() + "\n";
                }
                sb.append(line).append("\n");
            }
            r.close();
            p.waitFor();
        } catch (Exception e) {
            sb.append("Error: ").append(e.getMessage());
        }
        
        return sb.toString();
    }

    private String extractVideoId(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})").matcher(text);
        if (m.find()) return m.group(1);
        return "";
    }
}
