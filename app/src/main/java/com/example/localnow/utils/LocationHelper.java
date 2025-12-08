package com.example.localnow.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Helper class for GPS location operations
 * - Get user's current location
 * - Calculate distance between two points
 * - Filter events within specific radius
 */
public class LocationHelper {

    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int LOCATION_UPDATE_MIN_TIME = 5000; // 5 seconds
    private static final int LOCATION_UPDATE_MIN_DISTANCE = 10; // 10 meters

    private Context context;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;

    public interface LocationCallback {
        void onLocationReceived(double lat, double lng);
        void onLocationError(String error);
    }

    public LocationHelper(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Check if location permissions are granted
     */
    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request location permissions
     */
    public static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
            LOCATION_PERMISSION_REQUEST_CODE);
    }

    /**
     * Start tracking user's location
     */
    public void startLocationUpdates(LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted");
            return;
        }

        try {
            // Create location listener
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    lastKnownLocation = location;
                    callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {
                    callback.onLocationError("GPS is disabled");
                }
            };

            // Request location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_MIN_TIME,
                LOCATION_UPDATE_MIN_DISTANCE,
                locationListener
            );

            // Also try network provider for faster initial fix
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_MIN_TIME,
                    LOCATION_UPDATE_MIN_DISTANCE,
                    locationListener
                );
            }

            // Get last known location immediately
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation == null) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (lastLocation != null) {
                lastKnownLocation = lastLocation;
                callback.onLocationReceived(lastLocation.getLatitude(), lastLocation.getLongitude());
            }

        } catch (SecurityException e) {
            callback.onLocationError("Security exception: " + e.getMessage());
        } catch (Exception e) {
            callback.onLocationError("Error starting location updates: " + e.getMessage());
        }
    }

    /**
     * Stop tracking user's location
     */
    public void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                android.util.Log.e("LocationHelper", "Error stopping location updates", e);
            }
        }
    }

    /**
     * Calculate distance between two points using Haversine formula
     * @param lat1 Latitude of point 1
     * @param lng1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lng2 Longitude of point 2
     * @return Distance in kilometers
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS_KM = 6371;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Filter events within specified radius from user's location
     * @param events List of events to filter
     * @param userLat User's latitude
     * @param userLng User's longitude
     * @param radiusKm Radius in kilometers (e.g., 2.0 for 2km)
     * @return Filtered list of events within radius
     */
    public static java.util.List<com.example.localnow.model.Event> filterEventsByRadius(
            java.util.List<com.example.localnow.model.Event> events,
            double userLat,
            double userLng,
            double radiusKm) {

        java.util.List<com.example.localnow.model.Event> filteredEvents = new java.util.ArrayList<>();

        for (com.example.localnow.model.Event event : events) {
            double distance = calculateDistance(userLat, userLng, event.getLat(), event.getLng());

            if (distance <= radiusKm) {
                filteredEvents.add(event);
            }
        }

        return filteredEvents;
    }

    /**
     * Get last known location
     */
    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }
}
