package com.ecodrive.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TripDao {
    @Insert
    void insertTrip(TripEntity trip);

    @Query("SELECT * FROM trips ORDER BY timestampMs DESC")
    List<TripEntity> getAllTrips();

    @Query("SELECT * FROM trips WHERE timestampMs >= :startTimeMs AND timestampMs <= :endTimeMs ORDER BY timestampMs DESC")
    List<TripEntity> getTripsBetween(long startTimeMs, long endTimeMs);

    @Query("SELECT SUM(totalCo2Kg) FROM trips WHERE timestampMs >= :startTimeMs")
    double getTotalCo2Since(long startTimeMs);

    @Query("SELECT SUM(distanceKm) FROM trips WHERE timestampMs >= :startTimeMs")
    double getTotalDistanceSince(long startTimeMs);
}
