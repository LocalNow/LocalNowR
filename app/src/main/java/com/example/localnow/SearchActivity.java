package com.example.localnow;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.localnow.adapters.EventAdapter;
import com.example.localnow.utils.MockData;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private EventAdapter adapter;
    private List<com.example.localnow.model.Event> allEvents;
    private String currentQuery = "";
    private String currentCategory = "전체";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        RecyclerView recyclerView = findViewById(R.id.rv_search_results);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter with empty list
        allEvents = new ArrayList<>();
        adapter = new EventAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        android.widget.EditText searchBox = findViewById(R.id.et_search);
        ChipGroup chipGroup = findViewById(R.id.chipGroupCategory);

        // Fetch events from server
        fetchEvents(searchBox);

        // Setup Search Listener
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                filterEvents();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        // Setup Category Filter Listener
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentCategory = "전체";
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipAll)
                    currentCategory = "전체";
                else if (checkedId == R.id.chipFestival)
                    currentCategory = "축제";
                else if (checkedId == R.id.chipPerformance)
                    currentCategory = "공연";
                else if (checkedId == R.id.chipExhibition)
                    currentCategory = "전시";
                else if (checkedId == R.id.chipMarket)
                    currentCategory = "플리마켓";
                else if (checkedId == R.id.chipOther)
                    currentCategory = "기타";
            }
            filterEvents();
        });
    }

    private void fetchEvents(android.widget.EditText searchBox) {
        com.example.localnow.api.RetrofitClient.getApiService().getEvents()
                .enqueue(new retrofit2.Callback<com.example.localnow.model.EventResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            retrofit2.Response<com.example.localnow.model.EventResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            allEvents = response.body().getData();
                        } else {
                            allEvents = MockData.getEvents();
                        }
                        filterEvents();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.localnow.model.EventResponse> call,
                            Throwable t) {
                        allEvents = MockData.getEvents();
                        filterEvents();
                    }
                });
    }

    private void filterEvents() {
        List<com.example.localnow.model.Event> filteredList = new ArrayList<>();

        for (com.example.localnow.model.Event event : allEvents) {
            // Category filter
            if (!currentCategory.equals("전체")) {
                String eventCategory = event.getCategory();
                if (eventCategory == null || !eventCategory.equals(currentCategory)) {
                    continue;
                }
            }

            // Text search filter
            if (!currentQuery.isEmpty()) {
                String lowerQuery = currentQuery.toLowerCase();
                boolean matchesTitle = event.getTitle() != null &&
                        event.getTitle().toLowerCase().contains(lowerQuery);
                boolean matchesLocation = event.getLocation() != null &&
                        event.getLocation().toLowerCase().contains(lowerQuery);
                if (!matchesTitle && !matchesLocation) {
                    continue;
                }
            }

            filteredList.add(event);
        }
        adapter.updateList(filteredList);
    }
}
