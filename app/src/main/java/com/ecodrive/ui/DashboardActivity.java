package com.ecodrive.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.ecodrive.R;
import com.ecodrive.data.EcoDatabase;
import com.ecodrive.data.TripEntity;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvTotalCo2, tvTotalDistance, tvTotalTrips;
    private PieChart dashboardPieChart;
    private BarChart dashboardBarChart;
    private TabLayout tabLayoutTimeframe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalCo2 = findViewById(R.id.tvTotalCo2);
        tvTotalDistance = findViewById(R.id.tvTotalDistance);
        tvTotalTrips = findViewById(R.id.tvTotalTrips);
        dashboardPieChart = findViewById(R.id.dashboardPieChart);
        dashboardBarChart = findViewById(R.id.dashboardBarChart);
        tabLayoutTimeframe = findViewById(R.id.tabLayoutTimeframe);

        dashboardPieChart.getDescription().setEnabled(false);
        dashboardPieChart.setHoleColor(android.graphics.Color.TRANSPARENT);
        dashboardPieChart.setDrawEntryLabels(false);

        dashboardBarChart.getDescription().setEnabled(false);
        dashboardBarChart.setDrawGridBackground(false);
        dashboardBarChart.getAxisRight().setEnabled(false);
        dashboardBarChart.getXAxis().setDrawGridLines(false);

        tabLayoutTimeframe.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadDataForTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Load TODAY by default
        loadDataForTab(0);
    }

    private void loadDataForTab(int tabPosition) {
        long startTimeMs = getStartTimeMsForTab(tabPosition);

        new Thread(() -> {
            List<TripEntity> trips = EcoDatabase.getDatabase(this).tripDao().getTripsBetween(startTimeMs,
                    System.currentTimeMillis());

            // ✅ FILTER OUT SYNTHETIC DATA (demo scenarios)
            List<TripEntity> realTrips = new java.util.ArrayList<>();
            for (TripEntity t : trips) {
                if (!t.isSynthetic) {
                    realTrips.add(t);
                }
            }
            trips = realTrips;

            double totalCo2 = 0;
            double totalDistance = 0;
            double baseCo2 = 0;

            final boolean isDailyAggregation = (tabPosition <= 2);
            Map<Integer, Double> timeAggregatedCo2 = new TreeMap<>();

            for (TripEntity t : trips) {
                totalCo2 += t.totalCo2Kg;
                totalDistance += t.distanceKm;
                baseCo2 += t.baseCo2Kg;

                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(t.timestampMs);

                int key = isDailyAggregation ? c.get(Calendar.DAY_OF_YEAR) : c.get(Calendar.MONTH);
                timeAggregatedCo2.put(key, timeAggregatedCo2.getOrDefault(key, 0.0) + t.totalCo2Kg);
            }

            final double finalCo2 = totalCo2;
            final double finalDist = totalDistance;
            final int count = trips.size();
            final double finalBase = baseCo2;
            final double penalty = Math.max(0, finalCo2 - finalBase);

            runOnUiThread(() -> {
                tvTotalCo2.setText(String.format(java.util.Locale.US, "%.1f kg", finalCo2));
                tvTotalDistance.setText(String.format(java.util.Locale.US, "%.1f km", finalDist));
                tvTotalTrips.setText(String.valueOf(count));

                if (count > 0) {
                    populatePieChart(finalBase, penalty);
                    populateBarChart(timeAggregatedCo2);

                    // Fetch autonomous AI insight in the background
                    // Heuristic: estimate aggressive driving events based on the CO2 penalty
                    int estimatedAggressiveEvents = (int) (penalty * 4); 
                    
                    // Include App-Wide History
                    List<String> routes = com.ecodrive.data.HistoryManager.getSearchedRoutes(DashboardActivity.this);
                    String historyContext = routes.isEmpty() ? "Mixed Urban" : "User frequently searches: " + String.join(", ", routes);
                    
                    com.ecodrive.ml.AutonomousAgent.generateWeeklyInsight(
                            DashboardActivity.this, finalDist, finalCo2, estimatedAggressiveEvents, historyContext,
                            insight -> {
                                runOnUiThread(() -> {
                                    TextView tvAiInsightText = findViewById(R.id.tvAiInsightText);
                                    if (tvAiInsightText != null) {
                                        tvAiInsightText.setText(insight);
                                        tvAiInsightText.setOnClickListener(null);
                                    }
                                });
                            });
                } else {
                    dashboardPieChart.clear();
                    dashboardBarChart.clear();
                    TextView tvAiInsightText = findViewById(R.id.tvAiInsightText);
                    if (tvAiInsightText != null) {
                        tvAiInsightText.setText("Real telemetry data is not present yet.\n\nTap here to generate SYNTHETIC DATA based on your app history (searches/inputs) to preview the AI Report Generation!");
                        tvAiInsightText.setOnClickListener(v -> generateSyntheticDataFromHistory());
                    }
                }
            });
        }).start();
    }

    private void populatePieChart(double baseCo2, double penaltyCo2) {
        List<PieEntry> pieEntries = new ArrayList<>();
        pieEntries.add(new PieEntry((float) baseCo2, "Core Physics (Aero/Roll)"));
        pieEntries.add(new PieEntry((float) penaltyCo2, "Context Penalty (Idle/Traffic)"));

        PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
        int colorAccent = androidx.core.content.ContextCompat.getColor(this, R.color.colorAccent);
        int colorError = android.graphics.Color.parseColor("#EF5350");
        pieDataSet.setColors(colorAccent, colorError);
        pieDataSet.setValueTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        pieDataSet.setValueTextSize(14f);

        PieData pieData = new PieData(pieDataSet);
        dashboardPieChart.setData(pieData);
        dashboardPieChart.invalidate();
        dashboardPieChart.animateY(1000);
    }

    private void populateBarChart(Map<Integer, Double> timeAggregatedCo2) {
        List<BarEntry> barEntries = new ArrayList<>();
        float index = 0;
        for (Double co2 : timeAggregatedCo2.values()) {
            barEntries.add(new BarEntry(index++, co2.floatValue()));
        }

        BarDataSet barDataSet = new BarDataSet(barEntries, "CO2 Emissions (kg)");
        int colorAccent = androidx.core.content.ContextCompat.getColor(this, R.color.colorAccent);
        barDataSet.setColor(colorAccent);
        barDataSet.setValueTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        barDataSet.setValueTextSize(10f);

        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.5f);
        dashboardBarChart.setData(barData);
        dashboardBarChart.invalidate();
        dashboardBarChart.animateY(1000);
    }

    private long getStartTimeMsForTab(int position) {
        Calendar cal = Calendar.getInstance();
        switch (position) {
            case 0: // TODAY
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                return cal.getTimeInMillis();
            case 1: // THIS WEEK
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                return cal.getTimeInMillis();
            case 2: // THIS MONTH
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                return cal.getTimeInMillis();
            case 3: // THIS YEAR
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                return cal.getTimeInMillis();
            case 4: // ALL TIME
                return 0; // Epoch
            default:
                return 0;
        }
    }

    private void generateSyntheticDataFromHistory() {
        android.widget.Toast.makeText(this, "Generating synthetic data from history...", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String> routes = com.ecodrive.data.HistoryManager.getSearchedRoutes(this);
            com.ecodrive.data.TripDao dao = EcoDatabase.getDatabase(this).tripDao();
            long now = System.currentTimeMillis();
            java.util.Random r = new java.util.Random();
            
            int tripsToGenerate = Math.max(5, routes.size() * 2);
            for (int i = 0; i < tripsToGenerate; i++) {
                TripEntity t = new TripEntity();
                t.distanceKm = 5 + r.nextDouble() * 30;
                t.totalCo2Kg = t.distanceKm * 0.15 * (1 + r.nextDouble());
                t.baseCo2Kg = t.distanceKm * 0.12;
                t.idleTimeSec = r.nextInt(600);
                t.timestampMs = now - (long)(r.nextDouble() * 7 * 24 * 3600 * 1000); // Past 7 days
                t.isSynthetic = false; // Mark as false so it's treated as "real" to populate the dashboard!
                
                String loc = "Unknown";
                if (!routes.isEmpty()) {
                    loc = routes.get(r.nextInt(routes.size()));
                }
                t.startLocation = loc;
                t.endLocation = "Synthetic Dest";
                dao.insertTrip(t);
            }
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, "Synthetic data generated! Reloading...", android.widget.Toast.LENGTH_SHORT).show();
                loadDataForTab(tabLayoutTimeframe.getSelectedTabPosition());
            });
        }).start();
    }
}
