package com.example.localnow;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private Socket mSocket;
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText etMessage;
    private ImageView btnSend;
    private TextView tvConnectionStatus;
    private ImageView btnBack;
    private TextView tvChatTitle;
    private String eventId;
    private String eventTitle;

    // Dummy location for testing (Incheon Arts Center)
    // TODO: [Merge Conflict Resolution] Replace with real GPS location from
    // LocationManager when merging 'feature/current-location' branch
    private double currentLat = 37.4478;
    private double currentLng = 126.7001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get Event Info from Intent
        eventId = getIntent().getStringExtra("eventId");
        eventTitle = getIntent().getStringExtra("eventTitle");

        initViews();
        initSocket();
    }

    private void initViews() {
        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        btnBack = findViewById(R.id.btnBack);
        tvChatTitle = findViewById(R.id.tvChatTitle);

        if (eventTitle != null) {
            tvChatTitle.setText(eventTitle);
        }

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        btnBack.setOnClickListener(v -> finish());
    }

    private void initSocket() {
        try {
            // Replace with your server IP (10.0.2.2 for emulator)
            mSocket = IO.socket("http://10.0.2.2:5003");
        } catch (URISyntaxException e) {
            Log.e("ChatActivity", "Socket Init Error", e);
            return;
        }

        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on("receive_message", onNewMessage);
        mSocket.on("system_message", onSystemMessage); // Handle system messages (join/leave)

        mSocket.connect();
    }

    private Emitter.Listener onConnect = args -> runOnUiThread(() -> {
        tvConnectionStatus.setText("연결됨 🟢");
        tvConnectionStatus.setTextColor(android.graphics.Color.GREEN);

        // Send location immediately after connect
        sendLocation();

        // Join Room if eventId exists
        if (eventId != null) {
            joinRoom();
        }
    });

    private void joinRoom() {
        JSONObject data = new JSONObject();
        try {
            // Get user ID
            android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String userId = prefs.getString("user_id", "익명");

            data.put("room", eventId);
            data.put("nickname", userId);
            mSocket.emit("join", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private Emitter.Listener onDisconnect = args -> runOnUiThread(() -> {
        tvConnectionStatus.setText("연결 끊김 🔴");
        tvConnectionStatus.setTextColor(android.graphics.Color.RED);
    });

    private Emitter.Listener onConnectError = args -> runOnUiThread(() -> {
        tvConnectionStatus.setText("연결 오류 ⚠️");
        tvConnectionStatus.setTextColor(android.graphics.Color.RED);
        Log.e("ChatActivity", "Connection Error: " + (args.length > 0 ? args[0] : "Unknown"));
    });

    private Emitter.Listener onNewMessage = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        try {
            String nickname = data.getString("nickname");
            String message = data.getString("message");
            int distance = data.optInt("distance", 0);

            messages.add(new ChatMessage(nickname, message, distance));
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        } catch (JSONException e) {
            Log.e("ChatActivity", "Message Parse Error", e);
        }
    });

    private Emitter.Listener onSystemMessage = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        try {
            String message = data.getString("message");
            // Add system message to chat (optional: different view type)
            messages.add(new ChatMessage("System", message, 0));
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    });

    private void sendLocation() {
        JSONObject data = new JSONObject();
        try {
            data.put("lat", currentLat);
            data.put("lng", currentLng);
            mSocket.emit("update_location", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (message.isEmpty())
            return;

        JSONObject data = new JSONObject();
        try {
            // Get user ID from SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String userId = prefs.getString("user_id", "익명");

            data.put("nickname", userId);
            data.put("message", message);
            if (eventId != null) {
                data.put("room", eventId); // Include room ID for room-specific messages
            }
            mSocket.emit("send_message", data);

            // Add my message to list locally
            messages.add(new ChatMessage(userId, message, 0));
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);

            etMessage.setText("");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSocket != null) {
            // Leave room before disconnect
            if (eventId != null) {
                JSONObject data = new JSONObject();
                try {
                    android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                    String userId = prefs.getString("user_id", "익명");
                    data.put("room", eventId);
                    data.put("nickname", userId);
                    mSocket.emit("leave", data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            mSocket.disconnect();
            mSocket.off(Socket.EVENT_CONNECT, onConnect);
            mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);
            mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
            mSocket.off("receive_message", onNewMessage);
            mSocket.off("system_message", onSystemMessage);
        }
    }

    // Inner classes for Adapter and Model
    private static class ChatMessage {
        String nickname;
        String message;
        int distance;

        ChatMessage(String nickname, String message, int distance) {
            this.nickname = nickname;
            this.message = message;
            this.distance = distance;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private List<ChatMessage> mMessages;

        ChatAdapter(List<ChatMessage> messages) {
            mMessages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = mMessages.get(position);
            holder.tvNickname.setText(msg.nickname);
            holder.tvMessage.setText(msg.message);

            // Get user ID from SharedPreferences
            android.content.SharedPreferences prefs = holder.itemView.getContext().getSharedPreferences("user_prefs",
                    android.content.Context.MODE_PRIVATE);
            String currentUserId = prefs.getString("user_id", "익명");

            if (msg.nickname.equals(currentUserId)) {
                holder.tvDistance.setVisibility(View.GONE);
                holder.tvNickname.setTextColor(android.graphics.Color.BLUE);
            } else {
                holder.tvDistance.setVisibility(View.VISIBLE);
                holder.tvDistance.setText(msg.distance + "m 거리");
                holder.tvNickname.setTextColor(android.graphics.Color.GRAY);
            }
        }

        @Override
        public int getItemCount() {
            return mMessages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNickname, tvMessage, tvDistance;

            ViewHolder(View itemView) {
                super(itemView);
                tvNickname = itemView.findViewById(R.id.tvNickname);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvDistance = itemView.findViewById(R.id.tvDistance);
            }
        }
    }
}
