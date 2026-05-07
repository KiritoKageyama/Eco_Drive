package com.ecodrive.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.ecodrive.ml.OnDeviceLLMManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Dual-LLM Router: Routes AI queries through the on-device knowledge-base LLM first.
 * Supports multiple cloud fallbacks: Google Gemini and Anthropic Claude 3.1.
 */
public class DualLLMRouter {

    private static final String PREFS_NAME = "EcoDriveSettings";
    private static final String KEY_GEMINI_API = "gemini_api_key_v2";
    private static final String KEY_CLAUDE_API = "claude_api_key_v2";
    private static final String KEY_FALLBACK_PROVIDER = "fallback_provider";

    private static com.ecodrive.utils.SecurityManager securityManager;

    private static com.ecodrive.utils.SecurityManager getSecurityManager() {
        if (securityManager == null) {
            securityManager = new com.ecodrive.utils.SecurityManager();
        }
        return securityManager;
    }

    public enum CloudProvider { GEMINI, CLAUDE }

    public interface LLMCallback {
        void onResponse(String response, boolean usedFallback);
        void onError(String error);
    }

    public static String getGeminiApiKey(Context context) {
        String encrypted = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_GEMINI_API, "");
        return encrypted.isEmpty() ? "" : getSecurityManager().decrypt(encrypted);
    }

    public static void setGeminiApiKey(Context context, String apiKey) {
        String encrypted = getSecurityManager().encrypt(apiKey);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GEMINI_API, encrypted).apply();
    }

    public static String getClaudeApiKey(Context context) {
        String encrypted = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CLAUDE_API, "");
        return encrypted.isEmpty() ? "" : getSecurityManager().decrypt(encrypted);
    }

    public static void setClaudeApiKey(Context context, String apiKey) {
        String encrypted = getSecurityManager().encrypt(apiKey);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CLAUDE_API, encrypted).apply();
    }

    public static CloudProvider getFallbackProvider(Context context) {
        String p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_FALLBACK_PROVIDER, "GEMINI");
        return CloudProvider.valueOf(p);
    }

    public static void setFallbackProvider(Context context, CloudProvider provider) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_FALLBACK_PROVIDER, provider.name()).apply();
    }

    public static void queryDualLLM(Context context, String prompt, LLMCallback callback) {
        OnDeviceLLMManager llmManager = new OnDeviceLLMManager(context);
        
        // Re-enabling for high-end devices like iQOO 13.
        // If it still crashes, it is likely an incompatible Gemma 3 model format.
        if (llmManager.isModelAvailable()) {
            llmManager.generateResponse(prompt, new OnDeviceLLMManager.LLMResponseCallback() {
                @Override
                public void onResponse(String response) {
                    int confidence = estimateConfidence(prompt, response);
                    if (confidence > 80) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResponse(response, false));
                    } else {
                        routeToCloudFallback(context, prompt, response, confidence, callback);
                    }
                }

                @Override
                public void onError(String error) {
                    routeToCloudFallback(context, prompt, "[Local Model Error: " + error + "]", 0, callback);
                }
            });
        } else {
            int confidence = estimateConfidence(prompt, "");
            routeToCloudFallback(context, prompt, "", confidence, callback);
        }
    }

    private static void routeToCloudFallback(Context context, String prompt, String originalResponse, int confidence, LLMCallback callback) {
        CloudProvider provider = getFallbackProvider(context);
        String apiKey = (provider == CloudProvider.GEMINI) ? getGeminiApiKey(context) : getClaudeApiKey(context);

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onResponse(originalResponse + " [CONFIDENCE: " + confidence + " - NO CLOUD KEY]", false);
            return;
        }

        if (provider == CloudProvider.GEMINI) {
            queryGemini(prompt, apiKey, callback, originalResponse, confidence);
        } else {
            queryClaude(prompt, apiKey, callback, originalResponse, confidence);
        }
    }

    private static int estimateConfidence(String prompt, String response) {
        if (response == null || response.isEmpty()) return 0;
        String lower = prompt.toLowerCase();
        if (lower.contains("emission") || lower.contains("co2") || lower.contains("eco") || lower.contains("efficiency")) return 90;
        if (response.contains("specialized in vehicle emissions")) return 60;
        return 75;
    }

    private static void queryGemini(String prompt, String apiKey, LLMCallback callback, String fallbackText, int confidence) {
        new Thread(() -> {
            try {
                String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
                URL url = new URI(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject root = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", "You are EcoDrive AI. " + prompt);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                root.put("contents", contents);

                OutputStream os = conn.getOutputStream();
                os.write(root.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
                
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                if (responseCode >= 400) {
                    throw new Exception("HTTP " + responseCode + ": " + response.toString().replace("\"", "'"));
                }

                JSONObject resJson = new JSONObject(response.toString());
                JSONObject firstCandidate = resJson.getJSONArray("candidates").getJSONObject(0);
                String text;
                if (firstCandidate.has("content")) {
                    text = firstCandidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                } else {
                    text = "I cannot fulfill this request due to safety restrictions or an empty response.";
                }
                final String finalText = text;
                new Handler(Looper.getMainLooper()).post(() -> callback.onResponse(finalText, true));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResponse(fallbackText + " [GEMINI ERROR: " + e.getMessage() + "]", false));
            }
        }).start();
    }

    private static void queryClaude(String prompt, String apiKey, LLMCallback callback, String fallbackText, int confidence) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.anthropic.com/v1/messages";
                URL url = new URI(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject root = new JSONObject();
                root.put("model", "claude-3-5-sonnet-20240620"); // Claude 3.5 Sonnet (matches Claude 3.1 request)
                root.put("max_tokens", 1024);
                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", "You are EcoDrive AI. " + prompt);
                messages.put(message);
                root.put("messages", messages);

                OutputStream os = conn.getOutputStream();
                os.write(root.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
                
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                if (responseCode >= 400) {
                    throw new Exception("HTTP " + responseCode + ": " + response.toString().replace("\"", "'"));
                }

                JSONObject resJson = new JSONObject(response.toString());
                String text = resJson.getJSONArray("content").getJSONObject(0).getString("text");
                new Handler(Looper.getMainLooper()).post(() -> callback.onResponse(text, true));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResponse(fallbackText + " [CLAUDE ERROR: " + e.getMessage() + "]", false));
            }
        }).start();
    }
}
