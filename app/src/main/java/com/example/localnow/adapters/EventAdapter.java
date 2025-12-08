package com.example.localnow.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.localnow.R;
import com.example.localnow.model.Event;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<Event> eventList;

    public EventAdapter(List<Event> eventList) {
        this.eventList = eventList;
    }

    public void updateList(List<Event> newEvents) {
        this.eventList = newEvents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        holder.title.setText(event.getTitle());
        holder.subtitle.setText(event.getDate());

        // Set Icon based on category with dynamic color circle
        String category = event.getCategory();
        int color = getCategoryColor(category);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(color);
        holder.icon.setImageDrawable(circle);

        // Set Bookmark
        if (event.isBookmarked()) {
            holder.bookmark.setImageResource(R.drawable.ic_bookmark);
        } else {
            holder.bookmark.setImageResource(R.drawable.ic_bookmark_outline);
        }

        // Bookmark click handler
        holder.bookmark.setOnClickListener(v -> {
            com.example.localnow.utils.BookmarkManager bookmarkManager =
                com.example.localnow.utils.BookmarkManager.getInstance(holder.itemView.getContext());

            boolean currentlyBookmarked = event.isBookmarked();
            int eventId = event.getId();

            if (currentlyBookmarked) {
                // Remove bookmark via API
                com.example.localnow.api.RetrofitClient.getApiService()
                    .deleteBookmark(eventId)
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            if (response.isSuccessful()) {
                                // Update local BookmarkManager to trigger listeners
                                bookmarkManager.removeBookmark(eventId);
                                android.widget.Toast.makeText(holder.itemView.getContext(),
                                    "북마크 삭제됨", android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                android.widget.Toast.makeText(holder.itemView.getContext(),
                                    "삭제 실패", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            android.widget.Toast.makeText(holder.itemView.getContext(),
                                "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
            } else {
                // Add bookmark via API
                com.example.localnow.api.RetrofitClient.getApiService()
                    .addBookmark(new com.example.localnow.model.BookmarkRequest(eventId))
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            if (response.isSuccessful()) {
                                // Update local BookmarkManager to trigger listeners
                                bookmarkManager.addBookmark(eventId);
                                android.widget.Toast.makeText(holder.itemView.getContext(),
                                    "북마크 추가됨", android.widget.Toast.LENGTH_SHORT).show();
                            } else {
                                android.widget.Toast.makeText(holder.itemView.getContext(),
                                    "북마크 실패", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            android.widget.Toast.makeText(holder.itemView.getContext(),
                                "네트워크 오류", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
            }
        });

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(holder.itemView.getContext(),
                    com.example.localnow.EventDetailActivity.class);
            intent.putExtra("id", event.getId());
            intent.putExtra("title", event.getTitle());
            intent.putExtra("date", event.getDate());
            intent.putExtra("location", event.getLocation());
            intent.putExtra("image", event.getImage());
            intent.putExtra("isBookmarked", event.isBookmarked());
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    private int getCategoryColor(String category) {
        if (category == null) return 0xFFFF4757;
        switch (category) {
            case "축제": return 0xFFFFB6C1;
            case "문화/예술": return 0xFFFFEB3B;
            case "공연/전시": return 0xFF2196F3;
            case "교육/강좌": return 0xFF4DD0E1;
            default: return 0xFFFF4757;
        }
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView subtitle;
        ImageView bookmark;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.eventIcon);
            title = itemView.findViewById(R.id.eventTitle);
            subtitle = itemView.findViewById(R.id.eventSubtitle);
            bookmark = itemView.findViewById(R.id.bookmarkIcon);
        }
    }
}
