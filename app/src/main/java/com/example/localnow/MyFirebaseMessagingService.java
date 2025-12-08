package com.example.localnow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Handle FCM messages here.
        // If the application is in the foreground handle both data and notification
        // messages here.
        // Also if you intend on generating your own notifications as a result of a
        // received FCM
        // message, here is where that should be initiated.
        android.util.Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            sendNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody(),
                    null);
        } else if (remoteMessage.getData().size() > 0) {
            // Handle data payload
            java.util.Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");
            String title = data.get("title");
            String body = data.get("body");

            if ("dummy_event".equals(type)) {
                sendNotification(title, body, data);
            } else if ("scheduled_simulation".equals(type)) {
                sendNotification(title, body, data); // Can reuse same logic, just different text
            } else if (title != null && body != null) {
                sendNotification(title, body, null);
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        android.util.Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    private void sendRegistrationToServer(String token) {
        com.example.localnow.api.RetrofitClient.getApiService()
                .updateToken(new com.example.localnow.model.TokenRequest(token))
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.d(TAG, "Token updated on server");
                        } else {
                            android.util.Log.e(TAG, "Failed to update token: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        android.util.Log.e(TAG, "Network error updating token", t);
                    }
                });
    }

    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        Intent intent;
        // Both dummy_event and scheduled_simulation should open LoginActivity first
        // (for demo flow)
        if (data != null
                && ("dummy_event".equals(data.get("type")) || "scheduled_simulation".equals(data.get("type")))) {
            intent = new Intent(this, LoginActivity.class);
            intent.putExtra("event_id", Integer.parseInt(data.get("event_id")));
            intent.putExtra("event_title", data.get("event_title"));
            intent.putExtra("event_date", data.get("event_date"));
            intent.putExtra("event_location", data.get("event_location"));
            intent.putExtra("event_description", data.get("event_description"));
            intent.putExtra("event_category", data.get("event_category"));
            intent.putExtra("event_image", data.get("event_image"));
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String channelId = "fcm_banner_channel_v2"; // New channel to reset settings
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_MAX) // MAX for heads-up
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[] { 0, 1000, 500, 1000 }) // Longer vibration to ensure trigger
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "LocalNow 배너 알림",
                    NotificationManager.IMPORTANCE_HIGH); // IMPORTANCE_HIGH is required for heads-up
            channel.setDescription("팝업으로 뜨는 중요 알림");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[] { 0, 1000, 500, 1000 });
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.setBypassDnd(true); // Try to bypass DND if possible
            notificationManager.createNotificationChannel(channel);
        }

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
        android.util.Log.d(TAG, "Notification posted to channel " + channelId + " with ID: " + notificationId);
    }
}
