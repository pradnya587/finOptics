package com.example.finoptics;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import okhttp3.*;
import java.io.IOException;


public class InsightsFragment extends Fragment {

    private FirebaseFirestore db;
    private String userId;

    private MaterialButtonToggleGroup toggleTimeFilter;
    private boolean isLast3Months = false;

    private LinearLayout layoutCategoryBars;
    private TextView tvTopCategoryName, tvTopCategoryAmount;
    private TextView tvAiInsight, tvAiLoading;
    private ImageView imgTopCategory;
    private MaterialButton btnGenerateAiInsight;

    private final Map<Boolean, Map<String, Double>> cache = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_insights, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        bindViews(view);
        setupTimeToggle();
        setupAiButton();

        loadInsights(); // default = this month
        return view;
    }

    private void bindViews(View view) {
        toggleTimeFilter = view.findViewById(R.id.toggleTimeFilter);
        layoutCategoryBars = view.findViewById(R.id.layoutCategoryBars);

        tvTopCategoryName = view.findViewById(R.id.tvTopCategoryName);
        tvTopCategoryAmount = view.findViewById(R.id.tvTopCategoryAmount);
        imgTopCategory = view.findViewById(R.id.imgTopCategory);

        tvAiInsight = view.findViewById(R.id.tvAiInsight);
        tvAiLoading = view.findViewById(R.id.tvAiLoading);
        btnGenerateAiInsight = view.findViewById(R.id.btnGenerateAiInsight);
    }

    private void setupTimeToggle() {
        toggleTimeFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            isLast3Months = checkedId == R.id.btnLast3Months;
            loadInsights();
        });
    }

    private void loadInsights() {
        layoutCategoryBars.removeAllViews();
        tvAiInsight.setVisibility(View.GONE);

        if (cache.containsKey(isLast3Months)) {
            renderAll(cache.get(isLast3Months));
            return;
        }

        Timestamp startTimestamp = getStartTimestamp();
        Log.d("INSIGHTS_DEBUG", "Query startTimestamp: " + startTimestamp.toDate());

        db.collection("Users")
                .document(userId)
                .collection("Expenses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Log.d("INSIGHTS_DEBUG", "Total documents fetched: " + snapshot.size());

                    Map<String, Double> categoryTotals = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        // Print each document for debugging
                        Log.d("INSIGHTS_DEBUG", "Doc ID: " + doc.getId() + " Data: " + doc.getData());

                        String category = doc.getString("category");

                        // Try both amount field types
                        Double amount = doc.getDouble("amount");
                        if (amount == null) {
                            Object amtObj = doc.get("amount");
                            if (amtObj instanceof Long) amount = ((Long) amtObj).doubleValue();
                            else if (amtObj instanceof Double) amount = (Double) amtObj;
                        }

                        // Try both timestamp types
                        Timestamp ts = doc.getTimestamp("timestamp");
                        long tsMillis = 0;
                        if (ts != null) tsMillis = ts.toDate().getTime();
                        else {
                            Object tObj = doc.get("timestamp");
                            if (tObj instanceof Long) tsMillis = (Long) tObj;
                        }

                        if (category == null || amount == null) continue;

                        // Only include if after startTimestamp
                        if (tsMillis < startTimestamp.toDate().getTime()) continue;

                        categoryTotals.put(
                                category,
                                categoryTotals.getOrDefault(category, 0.0) + amount
                        );
                    }

                    cache.put(isLast3Months, categoryTotals);
                    renderAll(categoryTotals);
                })
                .addOnFailureListener(e -> Log.e("INSIGHTS", "Firestore error", e));
    }

    private void renderAll(Map<String, Double> categoryTotals) {
        if (categoryTotals.isEmpty()) {
            tvTopCategoryName.setText("No data");
            tvTopCategoryAmount.setText("₹0");
            imgTopCategory.setImageResource(R.drawable.ic_general);
            return;
        }
        renderTopCategory(categoryTotals);
        renderCategoryBreakdown(categoryTotals);
    }

    private void renderCategoryBreakdown(Map<String, Double> categoryTotals) {
        layoutCategoryBars.removeAllViews();
        double maxValue = Collections.max(categoryTotals.values());

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
        Collections.sort(sorted, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (Map.Entry<String, Double> entry : sorted) {
            View row = inflater.inflate(R.layout.item_category_bar, layoutCategoryBars, false);

            TextView tvCategory = row.findViewById(R.id.tvCategoryName);
            TextView tvAmount = row.findViewById(R.id.tvCategoryAmount);
            ProgressBar progressBar = row.findViewById(R.id.progressCategory);

            tvCategory.setText(entry.getKey());
            tvAmount.setText("₹" + Math.round(entry.getValue()));

            int percent = (int) ((entry.getValue() / maxValue) * 100);
            progressBar.setMax(100);

            ObjectAnimator.ofInt(progressBar, "progress", 0, percent)
                    .setDuration(600)
                    .start();

            layoutCategoryBars.addView(row);
        }
    }

    private void renderTopCategory(Map<String, Double> categoryTotals) {
        String topCategory = null;
        double maxAmount = 0;

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            if (entry.getValue() > maxAmount) {
                maxAmount = entry.getValue();
                topCategory = entry.getKey();
            }
        }

        if (topCategory == null) return;

        tvTopCategoryName.setText(topCategory);
        tvTopCategoryAmount.setText("₹" + Math.round(maxAmount));
        imgTopCategory.setImageResource(getCategoryIcon(topCategory));
    }

    private void setupAiButton() {
        btnGenerateAiInsight.setOnClickListener(v -> {

            Map<String, Double> data = cache.get(isLast3Months);
            if (data == null || data.isEmpty()) {
                tvAiInsight.setText("Not enough data to generate insights.");
                tvAiInsight.setVisibility(View.VISIBLE);
                return;
            }

            tvAiInsight.setVisibility(View.GONE);
            tvAiLoading.setVisibility(View.VISIBLE);

            // 1️⃣ Build prompt
            StringBuilder prompt = new StringBuilder();
            double totalSpent = 0; // 🔹 Added for context
            for (Map.Entry<String, Double> e : data.entrySet()) {
                prompt.append(e.getKey())
                        .append(": ")
                        .append(Math.round(e.getValue()))
                        .append("\n");
                totalSpent += e.getValue(); // 🔹 Summing up for the 'spent' field
            }

            // 🔹 1.5: Fetch Budget context before calling Function
            double finalTotalSpent = totalSpent;
            db.collection("Users").document(userId).get().addOnSuccessListener(userDoc -> {
                double budget = userDoc.contains("monthly_budget") ? userDoc.getDouble("monthly_budget") : 0;
                String prediction = (finalTotalSpent > budget) ? "Over budget" : "On track";

                // 2️⃣ Call Firebase Function
                OkHttpClient client = new OkHttpClient();

                JSONObject json = new JSONObject();
                try {
                    json.put("prompt", prompt.toString());
                    json.put("budget", budget);      // 🔹 New field
                    json.put("spent", finalTotalSpent); // 🔹 New field
                    json.put("prediction", prediction); // 🔹 New field
                } catch (Exception ignored) {}

                // 🔹 2.5: Get Auth Token (Required for your new Auth Guard)
                FirebaseAuth.getInstance().getCurrentUser().getIdToken(true).addOnSuccessListener(result -> {
                    String idToken = result.getToken();

                    RequestBody body = RequestBody.create(
                            json.toString(),
                            MediaType.parse("application/json")
                    );

                    Request request = new Request.Builder()
                            .url("https://us-central1-finoptics-79357.cloudfunctions.net/getAiInsight")
                            .addHeader("Authorization", "Bearer " + idToken) // 🔹 Added for security
                            .post(body)
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            if (getActivity() == null) return; // 🔹 Safety check
                            requireActivity().runOnUiThread(() -> {
                                tvAiLoading.setVisibility(View.GONE);
                                tvAiInsight.setVisibility(View.VISIBLE);
                                tvAiInsight.setText("Failed to generate insight.");
                            });
                        }

                        @Override

                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            // 🔹 Using try (...) here automatically closes the response body
                            try (ResponseBody responseBody = response.body()) {
                                if (!response.isSuccessful()) {
                                    updateUiWithError("Server error: " + response.code());
                                    return;
                                }

                                String res = responseBody.string();
                                JSONObject obj = new JSONObject(res);
                                String reply = obj.getString("reply");

                                if (getActivity() == null) return;
                                requireActivity().runOnUiThread(() -> {
                                    tvAiLoading.setVisibility(View.GONE);
                                    tvAiInsight.setVisibility(View.VISIBLE);
                                    tvAiInsight.setText(reply);
                                });

                            } catch (Exception e) {
                                updateUiWithError("AI returned an invalid response.");
                            }
                        }

                        // Small helper to keep those nested runOnUiThread brackets clean
                        private void updateUiWithError(String msg) {
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    tvAiLoading.setVisibility(View.GONE);
                                    tvAiInsight.setVisibility(View.VISIBLE);
                                    tvAiInsight.setText(msg);
                                });
                            }
                        }
                    });
                });
            });
        });
    }


    private Timestamp getStartTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (isLast3Months) cal.add(Calendar.MONTH, -3);
        else cal.set(Calendar.DAY_OF_MONTH, 1);

        return new Timestamp(cal.getTime());
    }

    private int getCategoryIcon(String category) {
        if (category == null) return R.drawable.ic_general;
        switch (category.toLowerCase()) {
            case "food": return R.drawable.ic_food;
            case "shopping": return R.drawable.ic_shopping;
            case "travel":
            case "transport": return R.drawable.ic_transport;
            case "entertainment": return R.drawable.ic_entertainment;
            case "health":
            case "medical": return R.drawable.ic_health;
            default: return R.drawable.ic_general;
        }
    }
}
