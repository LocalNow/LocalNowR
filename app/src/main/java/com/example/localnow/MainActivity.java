package com.example.localnow;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import com.example.localnow.utils.LocationHelper;

public class MainActivity extends AppCompatActivity {

    private com.kakao.vectormap.MapView mapView;
    private com.kakao.vectormap.KakaoMap kakaoMap;
    private java.util.List<com.example.localnow.model.Event> pendingEvents = new java.util.ArrayList<>();

    // GPS location tracking
    private LocationHelper locationHelper;
    private double userLatitude = 0.0;
    private double userLongitude = 0.0;
    private static final double RADIUS_KM = 2.0; // 2km radius filter
    private com.kakao.vectormap.label.Label userLocationMarker; // User location marker
    private com.kakao.vectormap.label.LabelLayer sharedLabelLayer; // Shared layer for all markers

    // Bookmark change listener
    private com.example.localnow.utils.BookmarkManager.BookmarkChangeListener bookmarkChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kakao Map initialization
        try {
            mapView = findViewById(R.id.map_view);
            mapView.post(() -> {
                try {
                    mapView.start(new com.kakao.vectormap.MapLifeCycleCallback() {
                        @Override
                        public void onMapDestroy() {
                            android.util.Log.d("MainActivity", "Map destroyed");
                        }

                        @Override
                        public void onMapError(Exception e) {
                            android.util.Log.e("MainActivity", "Map error: " + e.getMessage());
                            android.widget.Toast.makeText(MainActivity.this, "Map error: " + e.getMessage(),
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }, new com.kakao.vectormap.KakaoMapReadyCallback() {
                        @Override
                        public void onMapReady(com.kakao.vectormap.KakaoMap map) {
                            kakaoMap = map;
                            // Center map at Incheon Arts Center area where most events are
                            com.kakao.vectormap.LatLng eventCenterPosition = com.kakao.vectormap.LatLng.from(37.4478,
                                    126.7001);
                            com.kakao.vectormap.camera.CameraUpdate cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory
                                    .newCenterPosition(eventCenterPosition, 12); // Zoom out to see more events
                            kakaoMap.moveCamera(cameraUpdate);
                            android.util.Log.d("MainActivity",
                                    "Map initialized at Incheon Arts Center (37.4478, 126.7001)");

                            // Add markers if events were already fetched
                            if (!pendingEvents.isEmpty()) {
                                addMarkersToMap(pendingEvents);
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Failed to start Kakao Map", e);
                }
            });
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to initialize Kakao Map", e);
            android.widget.Toast.makeText(this, "Map unavailable: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }

        ImageView btnNotification, btnBookmark, btnSearch;

        btnNotification = findViewById(R.id.btnNotification);
        btnBookmark = findViewById(R.id.btnBookmark);
        btnSearch = findViewById(R.id.btnSearch);

        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, KeywordSettingsActivity.class);
            startActivity(intent);
        });

        btnBookmark.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BookmarkActivity.class);
            startActivity(intent);
        });

        btnSearch.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        ImageView btnCalendar = findViewById(R.id.btnCalendar);
        btnCalendar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            startActivity(intent);
        });

        ImageView btnChat = findViewById(R.id.btnChat);
        btnChat.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            startActivity(intent);
        });

        // My Location button - move camera to user's current location
        com.google.android.material.floatingactionbutton.FloatingActionButton btnMyLocation = findViewById(
                R.id.btnMyLocation);
        btnMyLocation.setOnClickListener(v -> {
            if (userLatitude != 0.0 && userLongitude != 0.0 && kakaoMap != null) {
                com.kakao.vectormap.LatLng userPosition = com.kakao.vectormap.LatLng.from(userLatitude, userLongitude);
                com.kakao.vectormap.camera.CameraUpdate cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory
                        .newCenterPosition(userPosition, 15);
                kakaoMap.moveCamera(cameraUpdate);
                android.util.Log.d("MainActivity",
                        "Camera moved to user location: " + userLatitude + ", " + userLongitude);
            } else {
                android.widget.Toast.makeText(this, "위치 정보를 가져오는 중입니다...", android.widget.Toast.LENGTH_SHORT).show();
                android.util.Log.w("MainActivity", "User location not available yet");
            }
        });

        // Initialize location tracking
        initializeLocationTracking();

        // Fetch Events (will populate bottom sheet automatically)
        fetchEvents();

        // Log Kakao Key Hash (only in logcat, no popup)
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),
                    android.content.pm.PackageManager.GET_SIGNATURES);
            for (android.content.pm.Signature signature : info.signatures) {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String keyHash = android.util.Base64.encodeToString(md.digest(), android.util.Base64.DEFAULT);
                android.util.Log.d("KeyHash", keyHash);
            }
        } catch (Exception e) {
            android.util.Log.e("KeyHash", "Failed to get key hash", e);
        }

        // Token Button Listener
        findViewById(R.id.btn_get_token).setOnClickListener(v -> {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    android.util.Log.w("LocalNow", "Fetching FCM registration token failed", task.getException());
                    android.widget.Toast.makeText(MainActivity.this, "Token Fail: " + task.getException().getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                String token = task.getResult();
                android.util.Log.d("LocalNow", "FCM Token: " + token);
                android.widget.Toast.makeText(MainActivity.this, "Token: " + token, android.widget.Toast.LENGTH_SHORT)
                        .show();
                // Send token to server
                sendTokenToServer(token);
            });
        });

        // Auto Fetch Token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                android.util.Log.w("LocalNow", "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            android.util.Log.d("LocalNow", "FCM Token: " + token);
            sendTokenToServer(token);
        });

        // Check for notification extras (Deep Link)
        if (getIntent().hasExtra("event_id")) {
            Intent detailIntent = new Intent(this, EventDetailActivity.class);
            detailIntent.putExtras(getIntent());
            startActivity(detailIntent);
        }

        // Register bookmark change listener
        bookmarkChangeListener = (eventId, isBookmarked) -> {
            // Refresh events when bookmark changes
            runOnUiThread(() -> fetchEvents());
        };
        com.example.localnow.utils.BookmarkManager.getInstance(this).addListener(bookmarkChangeListener);
    }

    private void fetchEvents() {
        com.example.localnow.api.RetrofitClient.getApiService().getEvents()
                .enqueue(new retrofit2.Callback<com.example.localnow.model.EventResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            retrofit2.Response<com.example.localnow.model.EventResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            java.util.List<com.example.localnow.model.Event> events = response.body().getData();

                            if (events != null && !events.isEmpty()) {
                                android.util.Log.d("MainActivity", "✅ Fetched " + events.size() + " events");
                                // Store events for when map is ready
                                pendingEvents = events;
                                // Update bottom sheet with real data
                                updateBottomSheetWithEvents(events);
                                // Add markers to map (if map is ready)
                                addMarkersToMap(events);
                                // Note: Notifications are now handled server-side for bookmarked events only
                            } else {
                                android.util.Log.w("MainActivity", "⚠️ No events found in response");
                                android.widget.Toast.makeText(MainActivity.this, "이벤트 데이터가 없습니다",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            android.util.Log.w("MainActivity", "⚠️ Response unsuccessful: " + response.code());
                            android.widget.Toast.makeText(MainActivity.this, "데이터를 불러오지 못했습니다",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            Throwable t) {
                        android.util.Log.e("MainActivity", "❌ Failed to fetch events: " + t.getMessage());
                        android.widget.Toast.makeText(MainActivity.this, "서버 연결 실패: " + t.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Get or create the shared label layer for all markers
     */
    private com.kakao.vectormap.label.LabelLayer getOrCreateLabelLayer() {
        if (kakaoMap == null)
            return null;

        if (sharedLabelLayer == null) {
            com.kakao.vectormap.label.LabelManager labelManager = kakaoMap.getLabelManager();
            sharedLabelLayer = labelManager.addLayer(
                    com.kakao.vectormap.label.LabelLayerOptions.from("eventLayer"));
            android.util.Log.d("MainActivity", "Created new label layer");
        }
        return sharedLabelLayer;
    }

    private void addMarkersToMap(java.util.List<com.example.localnow.model.Event> events) {
        if (kakaoMap == null) {
            android.util.Log.w("MainActivity", "KakaoMap is not ready yet");
            return;
        }

        android.util.Log.d("MainActivity", "🗺️ Adding " + events.size() + " markers to map");

        // Get shared label layer
        com.kakao.vectormap.label.LabelLayer labelLayer = getOrCreateLabelLayer();
        if (labelLayer == null) {
            android.util.Log.e("MainActivity", "Failed to get label layer");
            return;
        }

        // Store events for click
        final java.util.Map<String, com.example.localnow.model.Event> eventMap = new java.util.HashMap<>();
        int count = 0;

        for (com.example.localnow.model.Event event : events) {
            double lat = event.getLat();
            double lng = event.getLng();
            if (lat == 0.0 || lng == 0.0)
                continue;

            try {
                // Get category color
                int markerColor = com.example.localnow.utils.ColorUtils.getCategoryColor(event.getCategory());

                // Create marker bitmap with category color
                int size = 32;
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size,
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setColor(markerColor);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);

                com.kakao.vectormap.label.LabelStyle style = com.kakao.vectormap.label.LabelStyle.from(bitmap);
                com.kakao.vectormap.label.LabelStyles styles = com.kakao.vectormap.label.LabelStyles.from(style);

                // Add jitter
                double jitterLat = (Math.random() - 0.5) * 0.0006;
                double jitterLng = (Math.random() - 0.5) * 0.0006;

                com.kakao.vectormap.LatLng position = com.kakao.vectormap.LatLng.from(lat + jitterLat, lng + jitterLng);
                String id = "m" + count;
                eventMap.put(id, event);

                labelLayer.addLabel(
                        com.kakao.vectormap.label.LabelOptions.from(id, position)
                                .setStyles(styles)
                                .setClickable(true));
                count++;
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Marker error: " + e.getMessage());
            }
        }

        android.util.Log.d("MainActivity", "✅ " + count + " markers added");

        // Click listener
        kakaoMap.setOnLabelClickListener((map, layer, label) -> {
            com.example.localnow.model.Event ev = eventMap.get(label.getLabelId());
            if (ev != null)
                showEventDetailDialog(ev);
            return true;
        });
    }

    // Calculate distance between two coordinates in meters
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void showEventDetailDialog(com.example.localnow.model.Event event) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(event.getTitle())
                .setMessage(
                        "📍 " + (event.getLocation() != null ? event.getLocation() : "장소 미정") + "\n\n" +
                                "📅 " + (event.getDate() != null ? event.getDate() : "날짜 미정") + "\n\n" +
                                "🏷️ " + (event.getCategory() != null ? event.getCategory() : "기타") + "\n\n" +
                                (event.getDescription() != null ? event.getDescription() : ""))
                .setPositiveButton("자세히 보기", (dialog, which) -> {
                    // Navigate to event detail
                    Intent intent = new Intent(MainActivity.this, EventDetailActivity.class);
                    intent.putExtra("event_id", event.getId());
                    intent.putExtra("event_title", event.getTitle());
                    intent.putExtra("event_date", event.getDate());
                    intent.putExtra("event_location", event.getLocation());
                    intent.putExtra("event_category", event.getCategory());
                    intent.putExtra("event_description", event.getDescription());
                    intent.putExtra("event_image", event.getImage());
                    startActivity(intent);
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void updateBottomSheetWithEvents(java.util.List<com.example.localnow.model.Event> events) {
        androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.viewPager);
        com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tabLayout);

        java.util.List<com.example.localnow.adapters.BottomSheetAdapter.PageData> dataList = new java.util.ArrayList<>();
        com.example.localnow.utils.BookmarkManager bookmarkManager = com.example.localnow.utils.BookmarkManager
                .getInstance(this);

        // Create pages from nearby event data (up to 5 events)
        for (int i = 0; i < Math.min(events.size(), 5); i++) {
            com.example.localnow.model.Event event = events.get(i);

            com.example.localnow.adapters.BottomSheetAdapter.PageData pageData = new com.example.localnow.adapters.BottomSheetAdapter.PageData(
                    event.getCategory() != null ? event.getCategory() : "행사",
                    event.getLocation() != null ? event.getLocation() : "",
                    event.getTitle(),
                    event.getDate() != null ? event.getDate() : "",
                    R.drawable.ic_marker_yellow,
                    v -> {
                        // Move map camera to event location first
                        if (kakaoMap != null && event.getLat() != 0 && event.getLng() != 0) {
                            com.kakao.vectormap.LatLng position = com.kakao.vectormap.LatLng.from(event.getLat(),
                                    event.getLng());
                            com.kakao.vectormap.camera.CameraUpdate cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory
                                    .newCenterPosition(position, 17);
                            kakaoMap.moveCamera(cameraUpdate);
                        }

                        // Then open detail activity
                        Intent intent = new Intent(MainActivity.this, EventDetailActivity.class);
                        intent.putExtra("id", event.getId());
                        intent.putExtra("title", event.getTitle());
                        intent.putExtra("date", event.getDate());
                        intent.putExtra("location", event.getLocation());
                        intent.putExtra("description", event.getDescription());
                        intent.putExtra("image", event.getImage());
                        startActivity(intent);
                    });

            // Add event info for map navigation
            pageData.setEventInfo(event.getId(), event.getLat(), event.getLng());

            // Set initial bookmark state from BookmarkManager
            boolean isBookmarked = bookmarkManager.isBookmarked(event.getId());
            pageData.setBookmarked(isBookmarked);

            // Add bookmark click handler
            pageData.setBookmarkClickListener((eventId, isBookmarked1) -> {
                android.util.Log.d("MainActivity", "Bookmark toggled for event " + eventId + ": " + isBookmarked1);

                if (isBookmarked1) {
                    // Add bookmark via API
                    com.example.localnow.api.RetrofitClient.getApiService()
                            .addBookmark(new com.example.localnow.model.BookmarkRequest(eventId))
                            .enqueue(new retrofit2.Callback<Void>() {
                                @Override
                                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                                    if (response.isSuccessful()) {
                                        // Update local BookmarkManager to trigger listeners
                                        bookmarkManager.addBookmark(eventId);
                                        android.widget.Toast.makeText(MainActivity.this, "북마크 추가됨",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    } else {
                                        android.widget.Toast.makeText(MainActivity.this, "북마크 실패",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                                    android.widget.Toast
                                            .makeText(MainActivity.this, "네트워크 오류", android.widget.Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });
                } else {
                    // Remove bookmark via API
                    com.example.localnow.api.RetrofitClient.getApiService()
                            .deleteBookmark(eventId)
                            .enqueue(new retrofit2.Callback<Void>() {
                                @Override
                                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                                    if (response.isSuccessful()) {
                                        // Update local BookmarkManager to trigger listeners
                                        bookmarkManager.removeBookmark(eventId);
                                        android.widget.Toast.makeText(MainActivity.this, "북마크 삭제됨",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    } else {
                                        android.widget.Toast
                                                .makeText(MainActivity.this, "삭제 실패", android.widget.Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                }

                                @Override
                                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                                    android.widget.Toast
                                            .makeText(MainActivity.this, "네트워크 오류", android.widget.Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });
                }
            });

            dataList.add(pageData);
        }

        // If no events, show empty state
        if (dataList.isEmpty()) {
            dataList.add(new com.example.localnow.adapters.BottomSheetAdapter.PageData(
                    "알림",
                    "",
                    "등록된 이벤트가 없습니다",
                    "새로운 이벤트를 기다려주세요",
                    R.drawable.ic_marker_yellow,
                    v -> {
                    }));
        }

        com.example.localnow.adapters.BottomSheetAdapter adapter = new com.example.localnow.adapters.BottomSheetAdapter(
                dataList, RADIUS_KM);
        viewPager.setAdapter(adapter);

        new com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    // Get PageData for the current position and set tab text
                    com.example.localnow.adapters.BottomSheetAdapter.PageData pageData = dataList.get(position);
                    tab.setText(pageData.getPageTitle());
                }).attach();
    }

    private void sendTokenToServer(String token) {
        com.example.localnow.api.RetrofitClient.getApiService()
                .updateToken(new com.example.localnow.model.TokenRequest(token))
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            android.util.Log.d("MainActivity", "✅ FCM Token updated on server");
                        } else {
                            android.util.Log.e("MainActivity", "❌ Failed to update FCM token: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        android.util.Log.e("MainActivity", "❌ Network error updating FCM token", t);
                    }
                });
    }

    private void scheduleEventNotifications(java.util.List<com.example.localnow.model.Event> events) {
        androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(this);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());

        for (com.example.localnow.model.Event event : events) {
            String startStr = event.getStartDate();
            if (startStr == null || startStr.isEmpty())
                continue;

            try {
                java.util.Date startDate = sdf.parse(startStr);
                long startTime = startDate.getTime();
                long currentTime = System.currentTimeMillis();

                // Notification intervals: 3 days, 1 day, 12h, 6h, 1h
                long[] intervals = {
                        3 * 24 * 60 * 60 * 1000L,
                        1 * 24 * 60 * 60 * 1000L,
                        12 * 60 * 60 * 1000L,
                        6 * 60 * 60 * 1000L,
                        1 * 60 * 60 * 1000L
                };

                String[] intervalNames = { "3일 전", "1일 전", "12시간 전", "6시간 전", "1시간 전" };

                for (int i = 0; i < intervals.length; i++) {
                    long triggerTime = startTime - intervals[i];
                    long delay = triggerTime - currentTime;

                    if (delay > 0) {
                        androidx.work.Data data = new androidx.work.Data.Builder()
                                .putString("title", "이벤트 알림: " + event.getTitle())
                                .putString("message", "행사가 " + intervalNames[i] + "에 시작됩니다!")
                                .build();

                        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(
                                com.example.localnow.workers.NotificationWorker.class)
                                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                .setInputData(data)
                                .addTag("event_" + event.getId())
                                .build();

                        workManager.enqueue(request);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        android.util.Log.d("MainActivity", "Scheduled notifications for " + events.size() + " events");
    }

    /**
     * Initialize GPS location tracking
     */
    private void initializeLocationTracking() {
        locationHelper = new LocationHelper(this);

        // Check if permission is granted
        if (!locationHelper.hasLocationPermission()) {
            // Request permission
            LocationHelper.requestLocationPermission(this);
            return;
        }

        // Start tracking location
        startLocationTracking();
    }

    /**
     * Start tracking user's location
     */
    private void startLocationTracking() {
        locationHelper.startLocationUpdates(new LocationHelper.LocationCallback() {
            @Override
            public void onLocationReceived(double lat, double lng) {
                userLatitude = lat;
                userLongitude = lng;
                android.util.Log.d("MainActivity", "User location: " + lat + ", " + lng);

                // Update map camera to user's location on first location
                if (kakaoMap != null && userLatitude != 0.0) {
                    com.kakao.vectormap.LatLng userPosition = com.kakao.vectormap.LatLng.from(lat, lng);
                    com.kakao.vectormap.camera.CameraUpdate cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory
                            .newCenterPosition(userPosition, 15);
                    kakaoMap.moveCamera(cameraUpdate);

                    // Add or update user location marker
                    updateUserLocationMarker(userPosition);
                }

                // Refresh events with location filter
                if (!pendingEvents.isEmpty()) {
                    filterAndDisplayEvents();
                }
            }

            @Override
            public void onLocationError(String error) {
                android.util.Log.e("MainActivity", "Location error: " + error);
                android.widget.Toast.makeText(MainActivity.this,
                        "위치 정보를 가져올 수 없습니다: " + error,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Filter events by 2km radius and display them
     */
    private void filterAndDisplayEvents() {
        if (userLatitude == 0.0 || userLongitude == 0.0) {
            // No location yet, show all events
            addMarkersToMap(pendingEvents);
            updateBottomSheetWithEvents(pendingEvents);
            return;
        }

        // Filter events within 2km radius
        java.util.List<com.example.localnow.model.Event> filteredEvents = LocationHelper
                .filterEventsByRadius(pendingEvents, userLatitude, userLongitude, RADIUS_KM);

        android.util.Log.d("MainActivity", "Filtered " + filteredEvents.size() + " events within " + RADIUS_KM + "km");

        // Clear existing markers
        com.kakao.vectormap.label.LabelLayer labelLayer = getOrCreateLabelLayer();
        if (labelLayer != null) {
            labelLayer.removeAll();
            android.util.Log.d("MainActivity", "Cleared all markers from layer");
        }

        if (filteredEvents.isEmpty()) {
            android.widget.Toast.makeText(this,
                    "반경 " + RADIUS_KM + "km 내에 이벤트가 없습니다. 모든 이벤트를 표시합니다.",
                    android.widget.Toast.LENGTH_LONG).show();
            // Display all events if none are nearby
            addMarkersToMap(pendingEvents);
            updateBottomSheetWithEvents(pendingEvents);
        } else {
            // Display filtered events
            addMarkersToMap(filteredEvents);
            updateBottomSheetWithEvents(filteredEvents);
        }

        // Re-add user location marker after clearing and adding event markers
        if (userLatitude != 0.0 && userLongitude != 0.0) {
            com.kakao.vectormap.LatLng userPosition = com.kakao.vectormap.LatLng.from(userLatitude, userLongitude);
            updateUserLocationMarker(userPosition);
        }
    }

    /**
     * Add or update user location marker on the map
     */
    private void updateUserLocationMarker(com.kakao.vectormap.LatLng position) {
        if (kakaoMap == null) {
            android.util.Log.w("MainActivity", "Cannot add user marker - map not ready");
            return;
        }

        com.kakao.vectormap.label.LabelLayer labelLayer = getOrCreateLabelLayer();
        if (labelLayer == null) {
            android.util.Log.e("MainActivity", "Cannot add user marker - layer not available");
            return;
        }

        // Remove old marker if exists
        if (userLocationMarker != null) {
            try {
                labelLayer.remove(userLocationMarker);
                android.util.Log.d("MainActivity", "Removed old user location marker");
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Could not remove old marker: " + e.getMessage());
            }
            userLocationMarker = null;
        }

        // Create green marker for user location
        try {
            int size = 36;
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFF4CAF50); // 초록색
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);

            com.kakao.vectormap.label.LabelStyle style = com.kakao.vectormap.label.LabelStyle.from(bitmap);
            com.kakao.vectormap.label.LabelStyles styles = com.kakao.vectormap.label.LabelStyles.from(style);

            userLocationMarker = labelLayer.addLabel(
                    com.kakao.vectormap.label.LabelOptions.from("user_location", position)
                            .setStyles(styles)
                            .setClickable(false));
            android.util.Log.d("MainActivity",
                    "✓ User location marker added at: " + position.getLatitude() + ", " + position.getLongitude());
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "✗ Failed to add user location marker: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LocationHelper.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start tracking
                startLocationTracking();
            } else {
                // Permission denied
                android.widget.Toast.makeText(this,
                        "위치 권한이 필요합니다. 모든 이벤트를 표시합니다.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop location updates when activity is destroyed
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
        // Unregister bookmark change listener
        if (bookmarkChangeListener != null) {
            com.example.localnow.utils.BookmarkManager.getInstance(this).removeListener(bookmarkChangeListener);
        }
    }

}
