package com.example.localnow;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.localnow.api.RetrofitClient;
import com.example.localnow.model.KeywordRequest;
import com.example.localnow.model.KeywordResponse;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class KeywordSettingsActivity extends AppCompatActivity {

    private TextInputEditText etKeyword;
    private Button btnAdd;
    private ChipGroup chipGroup;
    private List<String> currentKeywords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyword_settings);

        etKeyword = findViewById(R.id.et_keyword);
        btnAdd = findViewById(R.id.btn_add_keyword);
        chipGroup = findViewById(R.id.chip_group_keywords);
        ImageView btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        btnAdd.setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (!keyword.isEmpty()) {
                if (currentKeywords.contains(keyword)) {
                    Toast.makeText(this, "이미 등록된 키워드입니다.", Toast.LENGTH_SHORT).show();
                } else {
                    addChip(keyword);
                    currentKeywords.add(keyword);
                    updateKeywordsOnServer();
                    etKeyword.setText("");
                }
            }
        });

        fetchKeywords();
    }

    private void fetchKeywords() {
        RetrofitClient.getApiService().getKeywords().enqueue(new Callback<KeywordResponse>() {
            @Override
            public void onResponse(Call<KeywordResponse> call, Response<KeywordResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> keywords = response.body().getKeywords();
                    if (keywords != null) {
                        currentKeywords.clear();
                        chipGroup.removeAllViews();
                        for (String keyword : keywords) {
                            if (!keyword.trim().isEmpty()) {
                                currentKeywords.add(keyword.trim());
                                addChip(keyword.trim());
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<KeywordResponse> call, Throwable t) {
                Toast.makeText(KeywordSettingsActivity.this, "키워드 로드 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addChip(String keyword) {
        Chip chip = new Chip(this);
        chip.setText(keyword);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            chipGroup.removeView(chip);
            currentKeywords.remove(keyword);
            updateKeywordsOnServer();
        });
        chipGroup.addView(chip);
    }

    private void updateKeywordsOnServer() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentKeywords.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(currentKeywords.get(i));
        }

        KeywordRequest request = new KeywordRequest(sb.toString());
        RetrofitClient.getApiService().updateKeywords(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Success silently or show toast?
                    // Toast.makeText(KeywordSettingsActivity.this, "저장됨",
                    // Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(KeywordSettingsActivity.this, "저장 실패", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(KeywordSettingsActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
