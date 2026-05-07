package com.ecodrive.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import org.osmdroid.util.GeoPoint;


import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility methods used across multiple activities.
 * Prevents code duplication between MainActivity and DynamicTestActivity.
 */
public class GeoUtils {

    /**
     * Decode an encoded polyline string (precision 5) into a list of GeoPoint.
     * This is the Google Encoded Polyline Algorithm Format used by OSRM.
     * @param encoded The encoded polyline string
     * @return List of GeoPoints representing the decoded polyline
     */
    public static List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> poly = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return poly;
        }

        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latitude = lat / 1e5;
            double longitude = lng / 1e5;
            poly.add(new GeoPoint(latitude, longitude));
        }

        return poly;
    }

    /**
     * Estimate road grade percentage from a terrain label string.
     * @param terrainType The terrain type label (e.g., "hilly", "rolling", "urban")
     * @return Estimated grade percentage
     */
    public static double estimateTerrainGradeFromLabel(String terrainType) {
        if (terrainType == null) {
            return 0.0;
        }

        String normalized = terrainType.toLowerCase();
        if (normalized.contains("mountainous")) {
            return 8.0;
        }
        if (normalized.contains("hilly")) {
            return 6.0;
        }
        if (normalized.contains("rolling")) {
            return 3.0;
        }
        if (normalized.contains("urban")) {
            return 1.5;
        }
        if (normalized.contains("arterial")) {
            return 0.8;
        }
        if (normalized.contains("plains") || normalized.contains("flat")) {
            return 0.3;
        }
        return 0.0;
    }

    /**
     * Convert a Drawable (including Vectors) to a Bitmap.
     * Useful for OSMDroid overlays that only accept Bitmaps.
     */
    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = 64; // Default
        if (height <= 0) height = 64; // Default

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}

