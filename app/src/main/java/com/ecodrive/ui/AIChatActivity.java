package com.ecodrive.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ecodrive.R;
import com.ecodrive.ml.OnDeviceLLMManager;
import com.ecodrive.ml.TripAIAssistant;
import com.ecodrive.physics.TripResult;
import com.ecodrive.physics.VehicleSpecs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AIChatActivity extends AppCompatActivity {
    private RecyclerView rvChat;
    private EditText etMessage;
    private FloatingActionButton btnSend;
    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private OnDeviceLLMManager llmManager;
    private TripResult currentTrip;
    private VehicleSpecs currentVehicle;

    // Trip context received from MainActivity
    private double tripDistanceKm = 15.0;
    private double tripAvgSpeedKmh = 40.0;
    private double tripStopRatio = 0.2;
    private double tripTemperatureC = 25.0;
    private boolean tripAcOn = false;
    private String tripTerrainType = "urban";
    private String tripMajorRoads = "N/A";
    private double tripAirDensity = 1.225;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        // Setup toolbar with back navigation
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Eco-Assistant AI");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvChat = findViewById(R.id.rvChatMessages);
        etMessage = findViewById(R.id.etChatMessage);
        btnSend = findViewById(R.id.btnSendChat);

        llmManager = new OnDeviceLLMManager(this);

        // Get trip data from intent (safely)
        if (getIntent() != null) {
            try {
                TripResult tr = (TripResult) getIntent().getSerializableExtra("trip_result");
                if (tr != null) currentTrip = tr;
            } catch (Exception ignored) {}
            try {
                VehicleSpecs vs = (VehicleSpecs) getIntent().getSerializableExtra("vehicle_specs");
                if (vs != null) currentVehicle = vs;
            } catch (Exception ignored) {}

            tripDistanceKm = getIntent().getDoubleExtra("trip_distance_km", tripDistanceKm);
            tripAvgSpeedKmh = getIntent().getDoubleExtra("trip_avg_speed_kmh", tripAvgSpeedKmh);
            tripStopRatio = getIntent().getDoubleExtra("trip_stop_ratio", tripStopRatio);
            tripTemperatureC = getIntent().getDoubleExtra("trip_temperature_c", tripTemperatureC);
            tripAcOn = getIntent().getBooleanExtra("trip_ac_on", tripAcOn);
            tripTerrainType = getIntent().getStringExtra("trip_terrain_type") != null
                    ? getIntent().getStringExtra("trip_terrain_type") : tripTerrainType;
            tripMajorRoads = getIntent().getStringExtra("trip_major_roads") != null
                    ? getIntent().getStringExtra("trip_major_roads") : tripMajorRoads;
            tripAirDensity = getIntent().getDoubleExtra("trip_air_density", tripAirDensity);
        }

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        loadChatHistory();

        // Show initial greeting with trip summary if available (only if history is empty)
        if (messages.isEmpty()) {
            if (currentTrip != null) {
                String greeting = String.format(Locale.US,
                        "Hello! I'm your Eco-Assistant.\n\n" +
                        "Your latest trip emitted %.2f kg CO2 (range: %.2f–%.2f kg).\n" +
                        "Ask me anything — why emissions were high, how to improve, or about your vehicle.",
                        currentTrip.meanCo2Kg, currentTrip.lowerBoundCo2Kg, currentTrip.upperBoundCo2Kg);
                addMessage("AI", greeting);
            } else {
                addMessage("AI", "Hello! I'm your Eco-Assistant. Calculate a trip first to get personalized insights, or ask me general questions about eco-driving.");
            }
        }

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;

            addMessage("User", text);
            etMessage.setText("");
            processAIResponse(text);
        });
    }

    private void saveChatHistory() {
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            for (ChatMessage m : messages) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("s", m.sender);
                obj.put("t", m.text);
                array.put(obj);
            }
            getSharedPreferences("ChatHistory", MODE_PRIVATE).edit()
                    .putString("history", array.toString()).apply();
        } catch (Exception e) {
            com.ecodrive.utils.AppLog.e("CHAT", "Save failed", e);
        }
    }

    private void loadChatHistory() {
        try {
            String history = getSharedPreferences("ChatHistory", MODE_PRIVATE).getString("history", "[]");
            org.json.JSONArray array = new org.json.JSONArray(history);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                messages.add(new ChatMessage(obj.getString("s"), obj.getString("t")));
            }
            adapter.notifyDataSetChanged();
            if (!messages.isEmpty()) rvChat.scrollToPosition(messages.size() - 1);
        } catch (Exception e) {
            com.ecodrive.utils.AppLog.e("CHAT", "Load failed", e);
        }
    }

    private void addMessage(String sender, String text) {
        messages.add(new ChatMessage(sender, text));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);
        saveChatHistory();
        if ("User".equals(sender)) {
            com.ecodrive.data.HistoryManager.addChatPrompt(this, text);
        }
    }

    /**
     * Build a context-enriched prompt that includes trip data so the AI can give
     * personalized, data-driven responses instead of generic answers.
     */
    private String buildContextualPrompt(String userQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("User question: ").append(userQuery).append("\n\n");

        List<String> routes = com.ecodrive.data.HistoryManager.getSearchedRoutes(this);
        if (!routes.isEmpty()) {
            sb.append("User Recent Searched Locations/Routes (Use this context!):\n");
            for(String r : routes) sb.append("- ").append(r).append("\n");
            sb.append("\n");
        }

        if (currentTrip != null) {
            sb.append("Current trip context:\n");
            sb.append(String.format(Locale.US, "- CO2 emitted: %.3f kg (range: %.3f–%.3f)\n",
                    currentTrip.meanCo2Kg, currentTrip.lowerBoundCo2Kg, currentTrip.upperBoundCo2Kg));
            sb.append(String.format(Locale.US, "- Fuel consumed: %.2f L\n", currentTrip.meanFuelLitres));
            sb.append(String.format(Locale.US, "- Distance: %.1f km\n", tripDistanceKm));
            sb.append(String.format(Locale.US, "- Avg speed: %.1f km/h\n", tripAvgSpeedKmh));
            sb.append(String.format(Locale.US, "- Stop ratio: %.2f\n", tripStopRatio));
            sb.append(String.format(Locale.US, "- Temperature: %.1f°C\n", tripTemperatureC));
            sb.append("- AC: ").append(tripAcOn ? "ON" : "OFF").append("\n");
            sb.append("- Terrain: ").append(tripTerrainType).append("\n");
            sb.append("- Major roads: ").append(tripMajorRoads).append("\n");
        }

        if (currentVehicle != null) {
            sb.append(String.format(Locale.US, "- Vehicle: %s (%.0f kg, %s)\n",
                    currentVehicle.modelName, currentVehicle.massKg,
                    currentVehicle.fuelType != null ? currentVehicle.fuelType.name() : "UNKNOWN"));
        }

        return sb.toString();
    }

    private void processAIResponse(String query) {
        addMessage("AI", "Thinking...");

        // Build a context-enriched prompt
        String contextualPrompt = buildContextualPrompt(query);

        com.ecodrive.api.DualLLMRouter.queryDualLLM(this, contextualPrompt, new com.ecodrive.api.DualLLMRouter.LLMCallback() {
            @Override
            public void onResponse(String response, boolean usedFallback) {
                runOnUiThread(() -> {
                    if (!messages.isEmpty() && messages.get(messages.size() - 1).text.equals("Thinking...")) {
                        messages.remove(messages.size() - 1);
                        adapter.notifyItemRemoved(messages.size());
                    }
                    String cleanResponse = response.replaceAll("\\[CONFIDENCE: \\d+\\]", "").trim();
                    if (usedFallback) {
                        cleanResponse += "\n\n(Insights powered by Gemini API)";
                    }
                    addMessage("AI", cleanResponse);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (!messages.isEmpty() && messages.get(messages.size() - 1).text.equals("Thinking...")) {
                        messages.remove(messages.size() - 1);
                        adapter.notifyItemRemoved(messages.size());
                    }
                    addMessage("AI", "Error processing request: " + error);
                });
            }
        });
    }

    public static class ChatMessage {
        public String sender, text;
        public ChatMessage(String s, String t) { sender = s; text = t; }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private List<ChatMessage> msgs;
        public ChatAdapter(List<ChatMessage> m) { this.msgs = m; }

        @Override
        public int getItemCount() { return msgs.size(); }

        @Override
        public void onBindViewHolder(ViewHolder h, int p) {
            ChatMessage m = msgs.get(p);
            if ("AI".equals(m.sender)) {
                h.cardAi.setVisibility(View.VISIBLE);
                h.cardUser.setVisibility(View.GONE);
                h.tvAiText.setText(m.text);
            } else {
                h.cardUser.setVisibility(View.VISIBLE);
                h.cardAi.setVisibility(View.GONE);
                h.tvUserText.setText(m.text);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View cardAi, cardUser;
            TextView tvAiText, tvUserText;
            public ViewHolder(View v) {
                super(v);
                cardAi = v.findViewById(R.id.cardAiMessage);
                cardUser = v.findViewById(R.id.cardUserMessage);
                tvAiText = v.findViewById(R.id.tvAiText);
                tvUserText = v.findViewById(R.id.tvUserText);
            }
        }
    }
}