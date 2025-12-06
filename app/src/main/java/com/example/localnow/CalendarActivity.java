package com.example.localnow;

import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.localnow.adapters.EventAdapter;
import com.example.localnow.model.Event;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private RecyclerView recyclerView;
    private EventAdapter adapter;
    private List<Event> allEvents;
    private List<Event> bookmarkedEvents;
    private CardView cardBookmarkedDates;
    private TextView tvBookmarkedDates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        calendarView = findViewById(R.id.calendarView);
        recyclerView = findViewById(R.id.rv_calendar_events);
        cardBookmarkedDates = findViewById(R.id.cardBookmarkedDates);
        tvBookmarkedDates = findViewById(R.id.tvBookmarkedDates);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize
        allEvents = new ArrayList<>();
        bookmarkedEvents = new ArrayList<>();
        adapter = new EventAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // Fetch data
        fetchEvents();
        fetchBookmarks();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            updateEventsForDate(selectedDate);
        });
    }

    private void fetchEvents() {
        com.example.localnow.api.RetrofitClient.getApiService().getEvents()
                .enqueue(new retrofit2.Callback<com.example.localnow.model.EventResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            retrofit2.Response<com.example.localnow.model.EventResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Event> events = response.body().getData();
                            if (events != null) {
                                allEvents = events;
                                // Show today's events
                                Calendar today = Calendar.getInstance();
                                String todayStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                        today.get(Calendar.YEAR),
                                        today.get(Calendar.MONTH) + 1,
                                        today.get(Calendar.DAY_OF_MONTH));
                                updateEventsForDate(todayStr);
                            }
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.localnow.model.EventResponse> call, Throwable t) {
                        android.widget.Toast.makeText(CalendarActivity.this, "네트워크 오류",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchBookmarks() {
        com.example.localnow.api.RetrofitClient.getApiService().getBookmarks()
                .enqueue(new retrofit2.Callback<com.example.localnow.model.EventResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            retrofit2.Response<com.example.localnow.model.EventResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Event> bookmarks = response.body().getData();
                            if (bookmarks != null && !bookmarks.isEmpty()) {
                                bookmarkedEvents = bookmarks;
                                displayBookmarkedDates();
                            }
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.localnow.model.EventResponse> call, Throwable t) {
                        // Silent fail - bookmarks are optional
                    }
                });
    }

    private void displayBookmarkedDates() {
        Set<String> dates = new HashSet<>();
        for (Event event : bookmarkedEvents) {
            String startDate = event.getStartDate();
            if (startDate != null && !startDate.isEmpty()) {
                dates.add(startDate);
            }
        }

        if (!dates.isEmpty()) {
            cardBookmarkedDates.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            List<String> sortedDates = new ArrayList<>(dates);
            java.util.Collections.sort(sortedDates);
            for (int i = 0; i < sortedDates.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(sortedDates.get(i));
            }
            tvBookmarkedDates.setText(sb.toString());
        }
    }

    private void updateEventsForDate(String selectedDate) {
        List<Event> eventsOnDate = new ArrayList<>();

        for (Event event : allEvents) {
            if (isEventOnDate(event, selectedDate)) {
                eventsOnDate.add(event);
            }
        }

        adapter.updateList(eventsOnDate);
    }

    private boolean isEventOnDate(Event event, String date) {
        String startDate = event.getStartDate();
        String endDate = event.getEndDate();

        if (startDate == null || startDate.isEmpty()) {
            return false;
        }
        if (endDate == null || endDate.isEmpty()) {
            endDate = startDate;
        }

        return date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
    }
}
