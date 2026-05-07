package com.ecodrive.tracking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.ecodrive.R;
import com.ecodrive.ui.MainActivity;

/**
 * Foreground Service that maintains GPS tracking even when the app is in background.
 * Ensures the physics-ML model continues to receive real-time telemetry.
 */
public class EcoDriveTrackingService extends Service {
    private static final String CHANNEL_ID = "EcoDriveTrackingChannel";
    private static final int NOTIFICATION_ID = 1001;

    private LocationTracker tracker;
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        startForeground(NOTIFICATION_ID, getNotification("EcoDrive is monitoring your trip..."));

        tracker = new LocationTracker(this, (location, distanceSinceLastKm, speedKmh, contextEngine) -> {
            // Update notification with live metrics
            updateNotification(String.format(java.util.Locale.US, "Speed: %.1f km/h | Dist: %.2f km", speedKmh, tracker.getTripSegmentDistanceKm()));
            
            // Broadcast location update to MainActivity if visible
            Intent broadcast = new Intent("com.ecodrive.LOCATION_UPDATE");
            broadcast.putExtra("lat", location.getLatitude());
            broadcast.putExtra("lon", location.getLongitude());
            broadcast.putExtra("speed", speedKmh);
            broadcast.putExtra("distanceDelta", distanceSinceLastKm);
            sendBroadcast(broadcast);
        });

        tracker.startTracking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (tracker != null) {
            tracker.stopTracking();
        }
        isRunning = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EcoDrive Live Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EcoDrive Active")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, getNotification(content));
        }
    }
}
