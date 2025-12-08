package com.example.localnow;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.localnow.adapters.EventAdapter;
import com.example.localnow.model.Event;
import com.example.localnow.utils.MockData;
import java.util.ArrayList;
import java.util.List;

public class BookmarkActivity extends AppCompatActivity {
    private com.example.localnow.utils.BookmarkManager.BookmarkChangeListener bookmarkChangeListener;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark);

        recyclerView = findViewById(R.id.rv_bookmarks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fetchBookmarks(recyclerView);

        // Register bookmark change listener
        bookmarkChangeListener = (eventId, isBookmarked) -> {
            // Refresh bookmarks when bookmark changes
            runOnUiThread(() -> fetchBookmarks(recyclerView));
        };
        com.example.localnow.utils.BookmarkManager.getInstance(this).addListener(bookmarkChangeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister bookmark change listener
        if (bookmarkChangeListener != null) {
            com.example.localnow.utils.BookmarkManager.getInstance(this).removeListener(bookmarkChangeListener);
        }
    }

    private void fetchBookmarks(RecyclerView recyclerView) {
        com.example.localnow.api.RetrofitClient.getApiService().getBookmarks()
                .enqueue(new retrofit2.Callback<com.example.localnow.model.EventResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.localnow.model.EventResponse> call, retrofit2.Response<com.example.localnow.model.EventResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<Event> events = response.body().getData();
                            for (Event event : events) {
                                event.setBookmarked(true);
                            }
                            EventAdapter adapter = new EventAdapter(events);
                            recyclerView.setAdapter(adapter);
                        } else {
                            loadLocalBookmarks(recyclerView);
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.localnow.model.EventResponse> call, Throwable t) {
                        loadLocalBookmarks(recyclerView);
                        android.widget.Toast.makeText(BookmarkActivity.this, "로컬 북마크 표시 중",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadLocalBookmarks(RecyclerView recyclerView) {
        com.example.localnow.utils.BookmarkManager bookmarkManager = com.example.localnow.utils.BookmarkManager
                .getInstance(this);

        List<Event> allEvents = MockData.getEvents();
        List<Event> bookmarkedEvents = new ArrayList<>();

        for (Event event : allEvents) {
            if (bookmarkManager.isBookmarked(event.getId())) {
                event.setBookmarked(true);
                bookmarkedEvents.add(event);
            }
        }

        EventAdapter adapter = new EventAdapter(bookmarkedEvents);
        recyclerView.setAdapter(adapter);
    }
}
