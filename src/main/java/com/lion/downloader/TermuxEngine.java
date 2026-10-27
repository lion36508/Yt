package com.lion.downloader;

import android.content.Context;

/**
 * Legacy compatibility wrapper retained for source compatibility.
 *
 * NEXORA no longer executes shell-based extractors or attempts to bypass
 * platform access controls. Direct HTTP(S) media URLs are handled by the
 * native downloader in DashboardActivity.
 */
public class TermuxEngine {
    public TermuxEngine(Context ctx) {
        // Kept for older callers; no external process is started.
    }

    public String exec(String cmd) {
        return "ERROR: shell extraction is disabled; use a permitted direct HTTP(S) media URL.";
    }
}
