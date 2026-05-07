package com.ecodrive.utils;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global Log Manager for the Developer Console.
 * Captures all critical app events and AI calls for transparency.
 */
public class AppLog {
    private static final String TAG = "EcoDrive_DEV";
    private static final int MAX_LOGS = 500;
    private static final List<String> logEntries = Collections.synchronizedList(new ArrayList<>());

    public static void d(String tag, String message) {
        String entry = String.format("[%s] %s: %s", System.currentTimeMillis(), tag, message);
        Log.d(tag, message);
        addLog(entry);
    }

    public static void i(String tag, String message) {
        String entry = String.format("[%s] %s: %s", System.currentTimeMillis(), tag, message);
        Log.i(tag, message);
        addLog(entry);
    }

    public static void e(String tag, String message, Throwable t) {
        String entry = String.format("[%s] ERROR %s: %s (%s)", System.currentTimeMillis(), tag, message, t.getMessage());
        Log.e(tag, message, t);
        addLog(entry);
    }

    private static void addLog(String entry) {
        logEntries.add(0, entry); // Newest first
        if (logEntries.size() > MAX_LOGS) {
            logEntries.remove(logEntries.size() - 1);
        }
    }

    public static List<String> getLogs() {
        return new ArrayList<>(logEntries);
    }

    public static void clear() {
        logEntries.clear();
    }
}
