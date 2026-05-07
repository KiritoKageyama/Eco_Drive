package com.ecodrive.ml;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages on-device LLM (Gemma 2B / 9B) using MediaPipe LLM Inference.
 * Handles optional model selection, download, and asynchronous inference.
 */
public class OnDeviceLLMManager {
    private static final String TAG = "OnDeviceLLM";
    private static final String PREFS_NAME = "EcoDriveSettings";
    private static final String KEY_MODEL_TYPE = "on_device_model_type";
    private static final String KEY_HF_TOKEN = "hf_access_token";


    // Official Model variants mirrored from Google AI Edge Gallery & User Selection
    public enum ModelType {
        GEMMA_3_1B("Gemma3-1B-IT.task", "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"),
        GEMMA_3_E2B("gemma-3n-E2B-it.task", "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task"),
        GEMMA_3_E4B("gemma-3n-E4B-it.task", "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task"),
        GEMMA_4_E2B("gemma-4-E2B-it.task", "https://huggingface.co/google/gemma-4-E2B-it/resolve/main/gemma-4-E2B-it-int4.task"),
        GEMMA_4_E4B("gemma-4-E4B-it.task", "https://huggingface.co/google/gemma-4-E4B-it/resolve/main/gemma-4-E4B-it-int4.task"),
        GEMMA_2B_IT("gemma-2b-it.bin", "https://huggingface.co/google/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin"),
        PHI_2("phi-2.bin", "https://huggingface.co/google/phi-2-it-cpu-int4/resolve/main/phi-2-it-cpu-int4.bin"),
        FALCON_7B("falcon-7b.bin", "https://huggingface.co/tiiuae/falcon-7b/resolve/main/falcon-7b-it-cpu-int4.bin");

        public final String filename;
        public final String url;

        ModelType(String filename, String url) {
            this.filename = filename;
            this.url = url;
        }
    }



    
    private LlmInference llmInference;
    private final Context context;
    private ModelType currentModelType = ModelType.GEMMA_2B_IT;

    private String manualUrl = null;

    public OnDeviceLLMManager(Context context) {

        this.context = context;
        loadPreferences();
    }

    private void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modelName = prefs.getString(KEY_MODEL_TYPE, ModelType.GEMMA_2B_IT.name());
        try {
            currentModelType = ModelType.valueOf(modelName);
        } catch (Exception e) {
            currentModelType = ModelType.GEMMA_2B_IT;
        }

    }

    public static void setModelType(Context context, ModelType type) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL_TYPE, type.name()).apply();
        com.ecodrive.utils.AppLog.i("LLM_MGR", "Model type changed to: " + type.name());
    }


    public ModelType getCurrentModelType() {
        return currentModelType;
    }

    public boolean isModelDownloaded() {
        File modelFile = new File(context.getFilesDir(), currentModelType.filename);
        if (modelFile.exists()) return true;

        // Check if model exists in assets (pre-shipped)
        try {
            java.io.InputStream is = context.getAssets().open(currentModelType.filename);
            if (is != null) {
                // Copy asset to internal storage for MediaPipe to access it via path
                copyAssetToFiles(currentModelType.filename);
                return true;
            }
        } catch (java.io.IOException ignored) {}

        return false;
    }

    private void copyAssetToFiles(String filename) {
        try (java.io.InputStream in = context.getAssets().open(filename);
             java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getFilesDir(), filename))) {
            byte[] buffer = new byte[1024 * 4];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to copy asset model: " + filename, e);
        }
    }

    public boolean isModelAvailable() {
        return isModelDownloaded();
    }


    /**
     * Downloads the selected Gemma model weights.
     */
    public void downloadModel(DownloadCallback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;
            try {
                String downloadUrl = (manualUrl != null && !manualUrl.isEmpty()) ? manualUrl : currentModelType.url;
                
                // Manual redirect handling for Hugging Face LFS
                boolean redirected = false;
                int redirectCount = 0;
                while (redirectCount < 5) {
                    URL url = new URL(downloadUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(false); // Handle manually
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    
                    String token = getHfToken(context);
                    // Only send Authorization to HuggingFace domains to avoid 403 on S3/CloudFront
                    if (token != null && !token.isEmpty() && downloadUrl.contains("huggingface.co")) {
                        connection.setRequestProperty("Authorization", "Bearer " + token);
                    }
                    
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    connection.connect();

                    int status = connection.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        downloadUrl = connection.getHeaderField("Location");
                        connection.disconnect();
                        redirectCount++;
                        continue;
                    }
                    break;
                }

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + connection.getResponseCode());
                }


                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                File targetFile = new File(context.getFilesDir(), currentModelType.filename);
                output = new FileOutputStream(targetFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final double progress = (double) total / fileLength;
                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(progress));
                        }
                    }
                    output.write(data, 0, count);
                }
                
                output.flush();
                com.ecodrive.utils.AppLog.i("LLM_DOWNLOAD", "Download successful: " + targetFile.getAbsolutePath());
                
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onComplete());
                }
            } catch (Exception e) {
                com.ecodrive.utils.AppLog.e("LLM_DOWNLOAD", "Download failed", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
                }
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }).start();
    }


    public void initializeModel() throws Exception {
        if (llmInference != null) return;
        
        if (!isModelDownloaded()) {
            throw new Exception("Model not downloaded yet.");
        }

        LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(new File(context.getFilesDir(), currentModelType.filename).getAbsolutePath())
                .setMaxTokens(512)
                .build();

        llmInference = LlmInference.createFromOptions(context, options);
    }

    public void generateResponse(String prompt, LLMResponseCallback callback) {
        new Thread(() -> {
            try {
                initializeModel();
                
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(() -> llmInference.generateResponse(prompt));
                
                String response;
                try {
                    // 15 seconds timeout to prevent indefinite hanging
                    response = future.get(15, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new Exception("Local LLM inference timed out after 15 seconds.");
                } finally {
                    executor.shutdownNow();
                }
                
                // Clean up any weird artifacts from prompt echoes if necessary
                if (response != null && response.startsWith(prompt)) {
                    response = response.substring(prompt.length()).trim();
                }
                
                String finalResponse = response;
                new Handler(Looper.getMainLooper()).post(() -> callback.onResponse(finalResponse));
            } catch (Exception e) {
                com.ecodrive.utils.AppLog.e("AI_ERROR", "Inference failure: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }






    public void setManualUrl(String url) {
        this.manualUrl = url;
    }

    public static void setHfToken(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_HF_TOKEN, token).apply();
    }

    public static String getHfToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_HF_TOKEN, "");
    }

    public interface DownloadCallback {


        void onProgress(double progress);
        void onComplete();
        void onError(String error);
    }

    public interface LLMResponseCallback {
        void onResponse(String response);
        void onError(String error);
    }
}
