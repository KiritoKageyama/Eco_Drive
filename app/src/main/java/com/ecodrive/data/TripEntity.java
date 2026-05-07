package com.ecodrive.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "trips")
public class TripEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestampMs;
    public double distanceKm;
    public double totalCo2Kg;
    public double baseCo2Kg; // Deterministic physics baseline
    public double idleTimeSec;
    public String startLocation;
    public String endLocation;
    public boolean isSynthetic; // True if demo/test data; prevents auto-save to production

    public TripEntity() {
    }

    @Ignore
    public TripEntity(long timestampMs, double distanceKm, double totalCo2Kg, double baseCo2Kg, double idleTimeSec,
            String startLocation, String endLocation) {
        this(timestampMs, distanceKm, totalCo2Kg, baseCo2Kg, idleTimeSec, startLocation, endLocation, false);
    }

    public TripEntity(long timestampMs, double distanceKm, double totalCo2Kg, double baseCo2Kg, double idleTimeSec,
            String startLocation, String endLocation, boolean isSynthetic) {
        this.timestampMs = timestampMs;
        this.distanceKm = distanceKm;
        this.totalCo2Kg = totalCo2Kg;
        this.baseCo2Kg = baseCo2Kg;
        this.idleTimeSec = idleTimeSec;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.isSynthetic = isSynthetic;
    }
}
