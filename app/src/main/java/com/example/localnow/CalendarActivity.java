package com.example.localnow;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.localnow.adapters.EventAdapter;
import com.example.localnow.model.Event;
import com.example.localnow.utils.EventDecorator;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarActivity extends AppCompatActivity {

    private MaterialCalendarView calendarView;
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

        calendarView.setOnDateChangedListener((widget, date, selected) -> {
            String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", date.getYear(), date.getMonth(),
                    date.getDay());
            updateEventsForDate(selectedDate);
        });

        // Set current date selected
        calendarView.setSelectedDate(CalendarDay.today());
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
                                markEventDays(events);

                                // Show today's events initially
                                CalendarDay today = CalendarDay.today();
                                String todayStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                                        today.getYear(), today.getMonth(), today.getDay());
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

    private void markEventDays(List<Event> events) {
        List<CalendarDay> days = new ArrayList<>();
        for (Event event : events) {
            String startStr = event.getStartDate();
            String endStr = event.getEndDate();

            if (startStr != null && endStr != null) {
                // Handle Range
                try {
                    CalendarDay startDay = parseDate(startStr);
                    CalendarDay endDay = parseDate(endStr);

                    if (startDay != null && endDay != null) {
                        CalendarDay current = startDay;
                        // Limit loop to avoid infinite loop or too many days (e.g. max 365 days)
                        int count = 0;
                        while (!current.isAfter(endDay) && count < 365) {
                            days.add(current);
                            // Add 1 day
                            java.time.LocalDate date = java.time.LocalDate.of(current.getYear(), current.getMonth(),
                                    current.getDay());
                            date = date.plusDays(1);
                            current = CalendarDay.from(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
                            count++;
                        }
                    }
                } catch (Exception e) {
                    // Fallback to single date
                    addSingleDate(days, event.getDate());
                }
            } else {
                // Fallback to single date
                addSingleDate(days, event.getDate());
            }
        }

        // Add Decorator
        calendarView.addDecorator(new EventDecorator(Color.parseColor("#FF6B6B"), days));
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
                        // Silent fail
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

    private CalendarDay parseDate(String dateStr) {
        // Supports YYYY-MM-DD or YYYYMMDD
        try {
            String cleanDate = dateStr.replace("-", "").replace(".", "");
            if (cleanDate.length() >= 8) {
                int year = Integer.parseInt(cleanDate.substring(0, 4));
                int month = Integer.parseInt(cleanDate.substring(4, 6));
                int day = Integer.parseInt(cleanDate.substring(6, 8));
                return CalendarDay.from(year, month, day);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void addSingleDate(List<CalendarDay> days, String dateStr) {
        CalendarDay day = parseDate(dateStr);
        if (day != null) {
            days.add(day);
        }
    }

    private void updateEventsForDate(String selectedDate) {
        List<Event> eventsOnDate = new ArrayList<>();
        // selectedDate format: YYYY-MM-DD
        String cleanSelected = selectedDate.replace("-", "");

        for (Event event : allEvents) {
            String startStr = event.getStartDate();
            String endStr = event.getEndDate();

            if (startStr != null && endStr != null) {
                // Range Check
                String cleanStart = startStr.replace("-", "").replace(".", "");
                String cleanEnd = endStr.replace("-", "").replace(".", "");

                if (cleanSelected.compareTo(cleanStart) >= 0 && cleanSelected.compareTo(cleanEnd) <= 0) {
                    eventsOnDate.add(event);
                    continue;
                }
            }

            // Fallback: Check exact match or start match
            String eventDate = event.getDate();
            if (eventDate != null) {
                String cleanEventDate = eventDate.replace("-", "").replace(".", "");
                if (cleanEventDate.startsWith(cleanSelected)) {
                    eventsOnDate.add(event);
                }
            }
        }

        adapter.updateList(eventsOnDate);
    }
}
