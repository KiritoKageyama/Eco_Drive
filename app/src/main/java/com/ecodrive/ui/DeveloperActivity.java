package com.ecodrive.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.ecodrive.R;
import com.ecodrive.ml.OnDeviceLLMManager;
import com.ecodrive.utils.AppLog;
import java.io.File;
import java.util.List;

public class DeveloperActivity extends AppCompatActivity {

    private TextView tvModelStatus, tvDevLogs;
    private Button btnDownloadModel;
    private ProgressBar pbDownload;
    private android.widget.EditText etManualModelUrl, etHfToken;
    private android.widget.Spinner spinnerModelSelect;


    private final Handler handler = new Handler(Looper.getMainLooper());


    private boolean isDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarDev);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvModelStatus = findViewById(R.id.tvModelStatus);
        tvDevLogs = findViewById(R.id.tvDevLogs);
        btnDownloadModel = findViewById(R.id.btnDownloadModel);
        pbDownload = findViewById(R.id.pbDownload);
        spinnerModelSelect = findViewById(R.id.spinnerModelSelect);
        etManualModelUrl = findViewById(R.id.etManualModelUrl);
        etHfToken = findViewById(R.id.etHfToken);

        etHfToken.setText(OnDeviceLLMManager.getHfToken(this));

        setupModelSpinner();



        findViewById(R.id.btnClearLogs).setOnClickListener(v -> {

            AppLog.clear();
            updateLogs();
        });

        btnDownloadModel.setOnClickListener(v -> startModelDownload());

        checkGemmaPresence();
        startLogRefreshLoop();
    }

    private void startModelDownload() {
        String hfToken = etHfToken.getText().toString().trim();
        OnDeviceLLMManager.setHfToken(this, hfToken);

        btnDownloadModel.setEnabled(false);
        btnDownloadModel.setText("DOWNLOADING...");
        pbDownload.setVisibility(View.VISIBLE);
        pbDownload.setProgress(0);

        OnDeviceLLMManager mgr = new OnDeviceLLMManager(this);

        String customUrl = etManualModelUrl.getText().toString().trim();
        if (!customUrl.isEmpty()) {
            mgr.setManualUrl(customUrl);
        }

        mgr.downloadModel(new OnDeviceLLMManager.DownloadCallback() {
            @Override
            public void onProgress(double progress) {
                runOnUiThread(() -> pbDownload.setProgress((int)(progress * 100)));
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    btnDownloadModel.setVisibility(View.GONE);
                    pbDownload.setVisibility(View.GONE);
                    checkGemmaPresence();
                    AppLog.i("DEV_CONS", "Model download complete.");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnDownloadModel.setEnabled(true);
                    btnDownloadModel.setText("RETRY DOWNLOAD");
                    pbDownload.setVisibility(View.GONE);
                    
                    String displayError = error;
                    if (error.contains("401")) {
                        displayError = "Unauthorized (401). Please enter a valid Hugging Face Token for gated models.";
                    }
                    
                    AppLog.e("DEV_CONS", "Download failed: " + displayError, null);
                });
            }

        });
    }


    private void setupModelSpinner() {
        OnDeviceLLMManager.ModelType[] models = OnDeviceLLMManager.ModelType.values();
        android.widget.ArrayAdapter<OnDeviceLLMManager.ModelType> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModelSelect.setAdapter(adapter);

        // Pre-select current
        OnDeviceLLMManager mgr = new OnDeviceLLMManager(this);
        OnDeviceLLMManager.ModelType current = mgr.getCurrentModelType();
        for (int i = 0; i < models.length; i++) {
            if (models[i] == current) {
                spinnerModelSelect.setSelection(i);
                break;
            }
        }

        spinnerModelSelect.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                OnDeviceLLMManager.ModelType selected = models[position];
                OnDeviceLLMManager.setModelType(DeveloperActivity.this, selected);
                checkGemmaPresence();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void checkGemmaPresence() {
        OnDeviceLLMManager mgr = new OnDeviceLLMManager(this);
        OnDeviceLLMManager.ModelType type = mgr.getCurrentModelType();
        File modelFile = new File(getFilesDir(), type.filename);
        
        // Update manual URL pre-fill
        if (etManualModelUrl.getText().toString().trim().isEmpty()) {
            etManualModelUrl.setText(type.url);
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Target Model: ").append(type.name()).append("\n");
        sb.append("Filename: ").append(type.filename).append("\n");
        sb.append("Local Path: ").append(modelFile.getAbsolutePath()).append("\n");

        if (modelFile.exists()) {
            long sizeMb = modelFile.length() / (1024 * 1024);
            sb.append("STATUS: PRESENT (VERIFIED)\n");
            sb.append("FILE SIZE: ").append(sizeMb).append(" MB");
            btnDownloadModel.setVisibility(View.GONE);
        } else {
            sb.append("STATUS: ABSENT\n");
            sb.append("EVIDENCE: File not found in app internal storage.");
            btnDownloadModel.setVisibility(View.VISIBLE);
        }
        tvModelStatus.setText(sb.toString());
    }



    private void startLogRefreshLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                updateLogs();
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void updateLogs() {
        List<String> logs = AppLog.getLogs();
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append("> ").append(log).append("\n\n");
        }
        if (logs.isEmpty()) {
            sb.append("> No logs captured yet. Activity starts now.");
        }
        tvDevLogs.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        super.onDestroy();
    }
}
