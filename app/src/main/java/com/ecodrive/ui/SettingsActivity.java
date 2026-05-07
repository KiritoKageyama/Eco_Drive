package com.ecodrive.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.ecodrive.R;
import com.ecodrive.api.DualLLMRouter;
import com.ecodrive.ml.OnDeviceLLMManager;
import com.ecodrive.utils.AppLog;
import com.ecodrive.data.EcoDatabase;
import com.ecodrive.data.TripEntity;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "EcoDriveSettings";
    private static final String KEY_PLACE_LEARNING = "place_learning_enabled";

    private EditText etGemini15ApiKey, etHfTokenSettings;
    private Spinner spinnerModelSelectSettings;
    private TextView tvModelStatusSettings;
    private ProgressBar pbModelDownloadSettings;
    private Button btnDownloadModelSettings;
    
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("System Settings");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // === Gemini 1.5 Setup ===
        etGemini15ApiKey = findViewById(R.id.etGemini15ApiKey);
        etGemini15ApiKey.setText(DualLLMRouter.getGeminiApiKey(this));
        findViewById(R.id.btnSaveGeminiKey).setOnClickListener(v -> {
            DualLLMRouter.setGeminiApiKey(this, etGemini15ApiKey.getText().toString().trim());
            Toast.makeText(this, "Gemini 1.5 Key Saved", Toast.LENGTH_SHORT).show();
        });

        // === On-Device Model Manager ===
        etHfTokenSettings = findViewById(R.id.etHfTokenSettings);
        spinnerModelSelectSettings = findViewById(R.id.spinnerModelSelectSettings);
        tvModelStatusSettings = findViewById(R.id.tvModelStatusSettings);
        pbModelDownloadSettings = findViewById(R.id.pbModelDownloadSettings);
        btnDownloadModelSettings = findViewById(R.id.btnDownloadModelSettings);

        etHfTokenSettings.setText(OnDeviceLLMManager.getHfToken(this));
        setupModelSpinner();

        btnDownloadModelSettings.setOnClickListener(v -> startModelDownload());

        // === Place Learning ===
        Switch switchPlaceLearning = findViewById(R.id.switchPlaceLearning);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        switchPlaceLearning.setChecked(prefs.getBoolean(KEY_PLACE_LEARNING, true));
        switchPlaceLearning.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean(KEY_PLACE_LEARNING, isChecked).apply();
        });

        // === Navigation & Export ===
        findViewById(R.id.btnGenerateWeeklyReport).setOnClickListener(v -> generatePdfReport());
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsvData());

        findViewById(R.id.btnOpenDeveloperConsole).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, DeveloperActivity.class));
        });

        checkModelPresence();
    }

    private void setupModelSpinner() {
        OnDeviceLLMManager.ModelType[] models = OnDeviceLLMManager.ModelType.values();
        String[] modelNames = new String[models.length];
        int selectedIndex = 0;
        OnDeviceLLMManager.ModelType current = new OnDeviceLLMManager(this).getCurrentModelType();

        for (int i = 0; i < models.length; i++) {
            modelNames[i] = models[i].name();
            if (models[i] == current) selectedIndex = i;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modelNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModelSelectSettings.setAdapter(adapter);
        spinnerModelSelectSettings.setSelection(selectedIndex);

        spinnerModelSelectSettings.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                OnDeviceLLMManager.setModelType(SettingsActivity.this, models[position]);
                checkModelPresence();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void checkModelPresence() {
        OnDeviceLLMManager mgr = new OnDeviceLLMManager(this);
        OnDeviceLLMManager.ModelType type = mgr.getCurrentModelType();
        File file = new File(getFilesDir(), type.filename);

        String status = "TARGET: " + type.name() + "\n";
        status += "FILE: " + type.filename + "\n";
        if (file.exists()) {
            status += "STATUS: PRESENT (READY)\n";
            status += "SIZE: " + (file.length() / (1024 * 1024)) + " MB";
            tvModelStatusSettings.setTextColor(0xFF00FF00); // Neon Green
            btnDownloadModelSettings.setEnabled(false);
            btnDownloadModelSettings.setText("MODEL ALREADY DOWNLOADED");
        } else {
            status += "STATUS: MISSING (DOWNLOAD REQUIRED)\n";
            status += "URL: " + type.url.substring(0, Math.min(type.url.length(), 40)) + "...";
            tvModelStatusSettings.setTextColor(0xFFFF0000); // Red
            btnDownloadModelSettings.setEnabled(true);
            btnDownloadModelSettings.setText("DOWNLOAD SELECTED MODEL");
        }
        tvModelStatusSettings.setText(status);
    }

    private void startModelDownload() {
        String token = etHfTokenSettings.getText().toString().trim();
        OnDeviceLLMManager.setHfToken(this, token);

        btnDownloadModelSettings.setEnabled(false);
        btnDownloadModelSettings.setText("DOWNLOADING...");
        pbModelDownloadSettings.setVisibility(View.VISIBLE);
        pbModelDownloadSettings.setProgress(0);

        OnDeviceLLMManager mgr = new OnDeviceLLMManager(this);
        mgr.downloadModel(new OnDeviceLLMManager.DownloadCallback() {
            @Override
            public void onProgress(double progress) {
                runOnUiThread(() -> pbModelDownloadSettings.setProgress((int) (progress * 100)));
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    btnDownloadModelSettings.setEnabled(true);
                    btnDownloadModelSettings.setText("DOWNLOAD COMPLETE");
                    pbModelDownloadSettings.setVisibility(View.GONE);
                    checkModelPresence();
                    Toast.makeText(SettingsActivity.this, "Model Downloaded Successfully", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnDownloadModelSettings.setEnabled(true);
                    btnDownloadModelSettings.setText("RETRY DOWNLOAD");
                    pbModelDownloadSettings.setVisibility(View.GONE);
                    Toast.makeText(SettingsActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exportCsvData() {
        Toast.makeText(this, "Exporting CSV...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<TripEntity> trips = EcoDatabase.getDatabase(this).tripDao().getAllTrips();
                StringBuilder csv = new StringBuilder("ID,Timestamp,Distance(km),Total_CO2(kg),Base_CO2(kg),Idle(sec),Start_Location,End_Location,Is_Synthetic\n");
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                
                for (TripEntity t : trips) {
                    csv.append(t.id).append(",")
                       .append(sdf.format(new Date(t.timestampMs))).append(",")
                       .append(t.distanceKm).append(",")
                       .append(t.totalCo2Kg).append(",")
                       .append(t.baseCo2Kg).append(",")
                       .append(t.idleTimeSec).append(",")
                       .append("\"").append(t.startLocation).append("\",")
                       .append("\"").append(t.endLocation).append("\",")
                       .append(t.isSynthetic).append("\n");
                }

                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadDir, "EcoDrive_Export_" + System.currentTimeMillis() + ".csv");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(csv.toString().getBytes());
                fos.close();

                handler.post(() -> Toast.makeText(this, "CSV Exported to Downloads: " + file.getName(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                AppLog.e("EXPORT", "CSV failed", e);
                handler.post(() -> Toast.makeText(this, "CSV Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void generatePdfReport() {
        Toast.makeText(this, "Generating PDF...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<TripEntity> trips = EcoDatabase.getDatabase(this).tripDao().getAllTrips();
                
                PdfDocument document = new PdfDocument();
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size
                PdfDocument.Page page = document.startPage(pageInfo);
                
                android.graphics.Canvas canvas = page.getCanvas();
                Paint paint = new Paint();
                paint.setColor(0xFF000000); // Black
                paint.setTextSize(16f);
                
                canvas.drawText("EcoDrive Weekly Report", 50, 50, paint);
                
                paint.setTextSize(12f);
                int y = 80;
                double totalDistance = 0;
                double totalCo2 = 0;
                
                for (TripEntity t : trips) {
                    if (!t.isSynthetic) {
                        totalDistance += t.distanceKm;
                        totalCo2 += t.totalCo2Kg;
                    }
                }
                
                canvas.drawText(String.format(Locale.US, "Total Real Distance: %.2f km", totalDistance), 50, y, paint);
                y += 20;
                canvas.drawText(String.format(Locale.US, "Total Real CO2 Emissions: %.2f kg", totalCo2), 50, y, paint);
                y += 40;
                
                canvas.drawText("Recent Trips:", 50, y, paint);
                y += 20;
                
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
                int count = 0;
                for (TripEntity t : trips) {
                    if (count++ > 20) break; // Limit to 20 on the first page to avoid overflow
                    String line = String.format(Locale.US, "%s | %.1f km | %.2f kg CO2 | %s", 
                            sdf.format(new Date(t.timestampMs)), t.distanceKm, t.totalCo2Kg, t.isSynthetic ? "(Demo)" : "(Real)");
                    canvas.drawText(line, 50, y, paint);
                    y += 15;
                }
                
                document.finishPage(page);
                
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadDir, "EcoDrive_Report_" + System.currentTimeMillis() + ".pdf");
                FileOutputStream fos = new FileOutputStream(file);
                document.writeTo(fos);
                document.close();
                fos.close();

                handler.post(() -> Toast.makeText(this, "PDF Exported to Downloads: " + file.getName(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                AppLog.e("EXPORT", "PDF failed", e);
                handler.post(() -> Toast.makeText(this, "PDF Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
