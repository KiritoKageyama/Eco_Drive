package com.ecodrive.tracking;

import android.annotation.SuppressLint;
import android.content.Context;
import com.ecodrive.data.PlaceLearningManager;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationTracker {

    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 50.0f;
    private static final long STOP_DETECTION_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
    private static final double STOP_MOVEMENT_THRESHOLD_M = 10.0; // 10 meters = stopped

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location, double distanceSinceLastKm, double speedKmh,
                TripContextEngine contextEngine);
    }

    public interface StopDetectionListener {
        /**
         * Called when the vehicle has been stationary for 5+ minutes
         * @param location the last recorded location
         * @param totalDistanceKm total distance traveled before stop
         * @param totalEmissionKg CO2 emitted before stop (estimated)
         */
        void onLongStopDetected(Location location, double totalDistanceKm, double totalEmissionKg);

        /**
         * Called when vehicle resumes movement after a detected stop
         */
        void onResumeAfterStop();
    }

    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationCallback locationCallback;
    private Location lastLocation;
    private final LocationUpdateListener listener;
    private StopDetectionListener stopListener;
    private boolean isTracking = false;

    // Stop detection
    private Location stopStartLocation = null;
    private long stopStartTime = 0;
    private boolean isCurrentlyStopped = false;
    private double tripSegmentDistanceKm = 0.0;
    private double tripSegmentEmissionKg = 0.0;

    // Core Engine
    private final TripContextEngine contextEngine;
    private PlaceLearningManager placeManager;
    private double cumulativeDistanceMeters = 0.0;

    public LocationTracker(Context context, LocationUpdateListener listener) {
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.listener = listener;
        this.contextEngine = new TripContextEngine();
        try {
            this.placeManager = new PlaceLearningManager(context);
        } catch (Exception ignored) {}

        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    processLocation(location);
                }
            }
        };
    }

    private boolean isAccurateEnough(Location location) {
        return location == null || !location.hasAccuracy() || location.getAccuracy() <= MAX_ACCEPTABLE_ACCURACY_METERS;
    }

    private void processLocation(Location location) {
        if (!isAccurateEnough(location)) {
            return;
        }

        double distanceKm = 0.0;
        double speedKmh = 0.0;

        if (lastLocation != null) {
            // Calculate distance delta
            distanceKm = lastLocation.distanceTo(location) / 1000.0;

            // Use GPS speed if available, else derive it
            if (location.hasSpeed()) {
                speedKmh = location.getSpeed() * 3.6; // m/s to km/h
            } else {
                double timeDeltaHours = (location.getTime() - lastLocation.getTime()) / 3600000.0;
                if (timeDeltaHours > 0) {
                    speedKmh = distanceKm / timeDeltaHours;
                }
            }
        } else {
            if (location.hasSpeed()) {
                speedKmh = location.getSpeed() * 3.6;
            }
        }

        // STOP DETECTION: Check if vehicle has moved less than threshold
        double distanceMovedMeters = lastLocation != null ? lastLocation.distanceTo(location) : 0;
        
        if (distanceMovedMeters < STOP_MOVEMENT_THRESHOLD_M && speedKmh < 2.0) {
            // Vehicle is stopped
            if (!isCurrentlyStopped) {
                // Just stopped
                isCurrentlyStopped = true;
                stopStartTime = System.currentTimeMillis();
                stopStartLocation = location;
            } else {
                // Accumulating stop time
                long stopDurationMs = System.currentTimeMillis() - stopStartTime;
                
                if (stopDurationMs >= STOP_DETECTION_THRESHOLD_MS) {
                    // Record this as a frequently visited place
                    if (placeManager != null) {
                        placeManager.recordVisit(location.getLatitude(), location.getLongitude());
                    }
                    // Trigger intermediate report
                    if (stopListener != null) {
                        stopListener.onLongStopDetected(location, tripSegmentDistanceKm, tripSegmentEmissionKg);
                    }
                    
                    // Reset segment trackers
                    tripSegmentDistanceKm = 0.0;
                    tripSegmentEmissionKg = 0.0;
                    stopStartTime = System.currentTimeMillis(); // Restart timer to avoid duplicate reports
                }
            }
        } else {
            // Vehicle is moving again
            if (isCurrentlyStopped) {
                // Just resumed after stop
                isCurrentlyStopped = false;
                if (stopListener != null) {
                    stopListener.onResumeAfterStop();
                }
            }
            // Accumulate segment distance and emission
            tripSegmentDistanceKm += distanceKm;
            // Rough estimation: 120g CO2 per km average
            tripSegmentEmissionKg += distanceKm * 0.120;
        }

        // 1. Update Context Engine State Machine
        contextEngine.updateState(location, speedKmh);

        // 2. Fetch Deep Context Map (Area Classifications) every ~200 meters or init
        if (cumulativeDistanceMeters == 0 || cumulativeDistanceMeters >= 200.0) {
            cumulativeDistanceMeters = 0.0;
            com.ecodrive.api.ContextualDataFetcher.fetchLocalContext(
                    location.getLatitude(), location.getLongitude(),
                    (roadType, signalCount) -> {
                        contextEngine.updateContext(roadType, signalCount);
                    });
        } else {
            if (lastLocation != null)
                cumulativeDistanceMeters += distanceKm * 1000.0;
        }

        lastLocation = location;
        if (listener != null && !isCurrentlyStopped) {
            // Only notify of movement when actively driving
            listener.onLocationUpdated(location, distanceKm, speedKmh, contextEngine);
        }
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        if (isTracking)
            return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        isTracking = true;
    }

    public void stopTracking() {
        if (!isTracking)
            return;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
        lastLocation = null;
    }

    public void setAutoEndListener(TripContextEngine.AutoEndListener listener) {
        if (contextEngine != null) {
            contextEngine.setAutoEndListener(listener);
        }
    }

    public void setStopDetectionListener(StopDetectionListener listener) {
        this.stopListener = listener;
    }

    public double getTripSegmentDistanceKm() {
        return tripSegmentDistanceKm;
    }

    public double getTripSegmentEmissionKg() {
        return tripSegmentEmissionKg;
    }

    @SuppressLint("MissingPermission")
    public void fetchSingleLocation(androidx.core.util.Consumer<Location> callback) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (isAccurateEnough(location)) {
                        lastLocation = location;
                        callback.accept(location);
                        return;
                    }

                    LocationRequest fallbackRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                            .setMinUpdateIntervalMillis(1000)
                            .setWaitForAccurateLocation(true)
                            .build();

                    LocationCallback singleShotCallback = new LocationCallback() {
                        @Override
                        public void onLocationResult(LocationResult locationResult) {
                            if (locationResult == null) {
                                return;
                            }

                            for (Location updatedLocation : locationResult.getLocations()) {
                                if (!isAccurateEnough(updatedLocation)) {
                                    continue;
                                }

                                lastLocation = updatedLocation;
                                fusedLocationClient.removeLocationUpdates(this);
                                callback.accept(updatedLocation);
                                return;
                            }
                        }
                    };

                    fusedLocationClient.requestLocationUpdates(fallbackRequest, singleShotCallback, Looper.getMainLooper());
                });
    }
    public TripContextEngine getContextEngine() {
        return contextEngine;
    }
}
