package com.example.localnow;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

public class EventDetailActivity extends AppCompatActivity {
    private int eventId;
    private boolean isBookmarked;
    private ImageView bookmarkIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        // Get data from Intent
        String title = getIntent().getStringExtra("title");
        if (title == null)
            title = getIntent().getStringExtra("event_title");

        String date = getIntent().getStringExtra("date");
        if (date == null)
            date = getIntent().getStringExtra("event_date");

        String location = getIntent().getStringExtra("location");
        if (location == null)
            location = getIntent().getStringExtra("event_location");

        String image = getIntent().getStringExtra("image");
        if (image == null)
            image = getIntent().getStringExtra("event_image");

        String category = getIntent().getStringExtra("category");
        if (category == null)
            category = getIntent().getStringExtra("event_category");

        String description = getIntent().getStringExtra("description");
        if (description == null)
            description = getIntent().getStringExtra("event_description");

        eventId = getIntent().getIntExtra("id", -1);
        if (eventId == -1)
            eventId = getIntent().getIntExtra("event_id", -1);

        // Use BookmarkManager for global state
        com.example.localnow.utils.BookmarkManager bookmarkManager = com.example.localnow.utils.BookmarkManager
                .getInstance(this);
        isBookmarked = bookmarkManager.isBookmarked(eventId);

        // UI References
        TextView tvTitle = findViewById(R.id.tv_event_title);
        TextView tvDate = findViewById(R.id.tv_event_date);
        TextView tvLocation = findViewById(R.id.tv_event_location);
        TextView tvCategory = findViewById(R.id.tv_category);
        TextView tvDescription = findViewById(R.id.tv_description);
        ImageView ivImage = findViewById(R.id.iv_event_image);
        bookmarkIcon = findViewById(R.id.iv_bookmark);
        ImageView btnBack = findViewById(R.id.btn_back);

        // Set Data
        tvTitle.setText(title != null ? title : "이벤트");
        tvDate.setText(formatDate(date));
        tvLocation.setText(location != null ? location : "장소 미정");
        tvCategory.setText(category != null ? category : "기타");
        tvDescription.setText(description != null && !description.isEmpty() ? description : "상세 정보가 없습니다.");

        // Load Image with Glide
        if (image != null && !image.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(image)
                    .placeholder(R.drawable.ic_map_placeholder)
                    .error(R.drawable.ic_map_placeholder)
                    .into(ivImage);
        }

        // Set Initial Bookmark State
        updateBookmarkIcon();

        // Back Button
        btnBack.setOnClickListener(v -> finish());

        // Bookmark Click Listener
        bookmarkIcon.setOnClickListener(v -> toggleBookmark());

        // Share Button
        final String finalTitle = title;
        final String finalDate = date;
        final String finalLocationForShare = location;

        findViewById(R.id.btn_share).setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, finalTitle);
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "🎉 " + finalTitle + "\n" +
                            "📅 " + formatDate(finalDate) + "\n" +
                            "📍 " + finalLocationForShare + "\n\n" +
                            "LocalNow 앱에서 확인하세요!");
            startActivity(Intent.createChooser(shareIntent, "공유하기"));
        });

        // Navigate Button
        final String finalLocation = location;
        findViewById(R.id.btn_navigate).setOnClickListener(v -> {
            if (finalLocation != null) {
                android.net.Uri gmmIntentUri = android.net.Uri.parse(
                        "geo:0,0?q=" + android.net.Uri.encode(finalLocation));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");

                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    // Fallback to web
                    Intent webIntent = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://maps.google.com/?q=" +
                                    android.net.Uri.encode(finalLocation)));
                    startActivity(webIntent);
                }
            }
        });
    }

    private String formatDate(String date) {
        if (date == null)
            return "날짜 미정";
        // Format: 20251225 -> 2025.12.25
        if (date.length() == 8 && date.matches("\\d+")) {
            return date.substring(0, 4) + "." + date.substring(4, 6) + "." + date.substring(6, 8);
        }
        return date;
    }

    private void updateBookmarkIcon() {
        if (isBookmarked) {
            bookmarkIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark, getTheme()));
        } else {
            bookmarkIcon.setColorFilter(getResources().getColor(android.R.color.darker_gray, getTheme()));
        }
    }

    private void toggleBookmark() {
        com.example.localnow.utils.BookmarkManager bookmarkManager = com.example.localnow.utils.BookmarkManager
                .getInstance(this);

        if (eventId == -1) {
            android.widget.Toast.makeText(this, "북마크할 수 없습니다", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        if (isBookmarked) {
            // Remove Bookmark
            com.example.localnow.api.RetrofitClient.getApiService().deleteBookmark(eventId)
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            bookmarkManager.removeBookmark(eventId);
                            isBookmarked = false;
                            updateBookmarkIcon();
                            android.widget.Toast.makeText(EventDetailActivity.this, "북마크 제거됨",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            bookmarkManager.removeBookmark(eventId);
                            isBookmarked = false;
                            updateBookmarkIcon();
                        }
                    });
        } else {
            // Add Bookmark
            com.example.localnow.model.BookmarkRequest request = new com.example.localnow.model.BookmarkRequest(
                    eventId);
            com.example.localnow.api.RetrofitClient.getApiService().addBookmark(request)
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            bookmarkManager.addBookmark(eventId);
                            isBookmarked = true;
                            updateBookmarkIcon();
                            android.widget.Toast.makeText(EventDetailActivity.this, "북마크 추가됨",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            bookmarkManager.addBookmark(eventId);
                            isBookmarked = true;
                            updateBookmarkIcon();
                        }
                    });
        }
    }
}
