package com.ecodrive.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages frequent stop locations and user-defined labels (Home, Work, etc.)
 * Uses SharedPreferences for lightweight local storage.
 */
public class PlaceLearningManager {
    private static final String PREFS_NAME = "EcoDrivePlaceLearning";
    private static final String KEY_SITES = "learned_sites";
    private final SharedPreferences prefs;

    public static class LearnedPlace {
        public double lat;
        public double lon;
        public String label; // "Home", "Work", "Custom"
        public int visitCount;

        public LearnedPlace(double lat, double lon, String label) {
            this.lat = lat;
            this.lon = lon;
            this.label = label;
            this.visitCount = 1;
        }

        @Override
        public String toString() {
            return lat + "," + lon + "," + label + "," + visitCount;
        }

        public static LearnedPlace fromString(String s) {
            String[] parts = s.split(",");
            if (parts.length < 4) return null;
            LearnedPlace p = new LearnedPlace(
                Double.parseDouble(parts[0]), 
                Double.parseDouble(parts[1]), 
                parts[2]);
            p.visitCount = Integer.parseInt(parts[3]);
            return p;
        }
    }

    public PlaceLearningManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordVisit(double lat, double lon) {
        List<LearnedPlace> places = getLearnedPlaces();
        boolean found = false;
        for (LearnedPlace p : places) {
            if (calculateDistance(p.lat, p.lon, lat, lon) < 0.05) { // 50m radius
                p.visitCount++;
                found = true;
                break;
            }
        }
        if (!found) {
            places.add(new LearnedPlace(lat, lon, "Unlabeled"));
        }
        savePlaces(places);
    }

    public void labelPlace(double lat, double lon, String label) {
        List<LearnedPlace> places = getLearnedPlaces();
        for (LearnedPlace p : places) {
            if (calculateDistance(p.lat, p.lon, lat, lon) < 0.05) {
                p.label = label;
                break;
            }
        }
        savePlaces(places);
    }

    public List<LearnedPlace> getLearnedPlaces() {
        String data = prefs.getString(KEY_SITES, "");
        List<LearnedPlace> list = new ArrayList<>();
        if (data.isEmpty()) return list;
        for (String s : data.split(";")) {
            LearnedPlace p = LearnedPlace.fromString(s);
            if (p != null) list.add(p);
        }
        return list;
    }

    private void savePlaces(List<LearnedPlace> places) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < places.size(); i++) {
            sb.append(places.get(i).toString());
            if (i < places.size() - 1) sb.append(";");
        }
        prefs.edit().putString(KEY_SITES, sb.toString()).apply();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = Math.toRadians(lat1 - lat2);
        double delta = Math.toRadians(lon1 - lon2);
        return Math.sqrt(theta * theta + delta * delta) * 111.0; // Rough km
    }
}
