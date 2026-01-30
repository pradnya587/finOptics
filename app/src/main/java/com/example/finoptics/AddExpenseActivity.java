package com.example.finoptics;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;


import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText etSmartInput;
    private Button btnSave;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private final Map<String, String> localVendorMap = new HashMap<>();
    private final Map<String, List<String>> keywordClusters = new HashMap<>();



    private static final String TAG = "FinOptics_Gemini";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etSmartInput = findViewById(R.id.etSmartInput);
        btnSave = findViewById(R.id.btnSaveExpense);
        progressBar = findViewById(R.id.progressBar);

        initLocalData();

        btnSave.setOnClickListener(v -> startSmartCategorization());
    }

    /* -------------------- LOCAL DATA (PRESERVED) -------------------- */

    private void initLocalData() {
        localVendorMap.put("zomato", "Food");
        localVendorMap.put("swiggy", "Food");
        localVendorMap.put("starbucks", "Food");
        localVendorMap.put("blinkit", "Shopping");
        localVendorMap.put("amazon", "Shopping");
        localVendorMap.put("uber", "Transport");
        localVendorMap.put("ola", "Transport");

        keywordClusters.put("Food",
                Arrays.asList("coffee", "tea", "pizza", "burger", "dinner", "lunch"));

        keywordClusters.put("Transport",
                Arrays.asList("petrol", "fuel", "bus", "metro", "auto", "cab"));

        keywordClusters.put("Shopping",
                Arrays.asList(
                        "clothes", "shoes", "mall", "grocery", "gift",
                        "makeup", "lipstick", "cosmetics", "skincare"
                ));

        keywordClusters.put("Health",
                Arrays.asList("doctor", "medicine", "gym", "hospital"));

        keywordClusters.put("Bills",
                Arrays.asList("electricity", "rent", "recharge", "wifi", "gas"));
    }

    /* -------------------- CORE FLOW (PRESERVED) -------------------- */

    private void startSmartCategorization() {
        String input = etSmartInput.getText().toString().trim();
        if (input.isEmpty()) return;

        double amount = extractAmount(input);
        String category = autoCategorize(input);

        if (!category.equals("Other") && amount > 0) {
            saveExpense(amount, category, input);
        } else {
            callGeminiAI(input, amount);
        }
    }

    /* -------------------- GEMINI CALL (PRESERVED) -------------------- */

    private void callGeminiAI(String input, double fallbackAmount) {
        progressBar.setVisibility(android.view.View.VISIBLE);
        btnSave.setEnabled(false);

        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject payload = new JSONObject();

            String prompt =
                    "You are an expense classification system.\n\n" +
                            "Choose EXACTLY ONE category from this list:\n" +
                            "- Food\n" +
                            "- Transport\n" +
                            "- Shopping\n" +
                            "- Health\n" +
                            "- Bills\n" +
                            "- Entertainment\n" +
                            "- Other\n\n" +
                            "Examples:\n" +
                            "pizza, burger, coffee → Food\n" +
                            "uber, petrol → Transport\n" +
                            "clothes, shoes, makeup, lipstick → Shopping\n" +
                            "doctor visit, medicine → Health\n" +
                            "electricity bill, rent → Bills\n\n" +
                            "Expense: \"" + input + "\"\n\n" +
                            "Return ONLY JSON:\n" +
                            "{ \"amount\": number, \"category\": string }";

            payload.put("prompt", prompt);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://geminirequest-vludjrerya-uc.a.run.app")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> fallbackSave(input, fallbackAmount));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String raw = response.body() != null ? response.body().string() : "";
                    Log.d("Gemini_Raw", raw);

                    runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(raw);
                            double amount = json.optDouble("amount", fallbackAmount);
                            String category = normalizeCategory(json.optString("category"));
                            saveExpense(amount, category, input);
                        } catch (Exception e) {
                            fallbackSave(input, fallbackAmount);
                        } finally {
                            progressBar.setVisibility(android.view.View.GONE);
                            btnSave.setEnabled(true);
                        }
                    });
                }
            });

        } catch (Exception e) {
            fallbackSave(input, fallbackAmount);
            progressBar.setVisibility(android.view.View.GONE);
            btnSave.setEnabled(true);
        }
    }

    private void fallbackSave(String input, double amount) {
        saveExpense(amount, "Other", input);
    }

    /* -------------------- HELPERS (PRESERVED) -------------------- */

    private double extractAmount(String input) {
        Matcher m = Pattern.compile("(\\d+(\\.\\d+)?)").matcher(input);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    private String autoCategorize(String input) {
        String text = input.toLowerCase();

        for (String vendor : localVendorMap.keySet()) {
            if (text.contains(vendor)) return localVendorMap.get(vendor);
        }

        for (Map.Entry<String, List<String>> entry : keywordClusters.entrySet()) {
            for (String word : entry.getValue()) {
                if (text.contains(word)) return entry.getKey();
            }
        }

        SharedPreferences prefs = getSharedPreferences("AI_Learning", MODE_PRIVATE);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (text.contains(e.getKey())) return (String) e.getValue();
        }

        return "Other";
    }

    private String normalizeCategory(String cat) {
        if (cat == null) return "Other";

        switch (cat.toLowerCase().trim()) {
            case "food": return "Food";
            case "transport": return "Transport";
            case "shopping": return "Shopping";
            case "health": return "Health";
            case "bills": return "Bills";
            case "entertainment": return "Entertainment";
            default: return "Other";
        }
    }

    /* -------------------- FIREBASE (PRESERVED) -------------------- */

    private void saveExpense(double amount, String category, String note) {
        if (mAuth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("category", category);
        data.put("note", note);
        data.put("timestamp", Timestamp.now());

        db.collection("Users")
                .document(mAuth.getCurrentUser().getUid())
                .collection("Expenses")
                .add(data)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(this,
                            "Saved to " + category,
                            Toast.LENGTH_SHORT).show();

                    detectTopAnomaly(amount, category);

                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Save failed",
                                Toast.LENGTH_SHORT).show());
    }



    /* -------------------- CORRECTED ANOMALY DETECTION -------------------- */

    private void detectTopAnomaly(double amount, String category) {

        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();

        final double expenseAmount = amount;
        final String expenseCategory = category;

        DocumentReference statsRef = db.collection("Users")
                .document(uid)
                .collection("Stats")
                .document("baseline");

        DocumentReference alertRef = db.collection("Users")
                .document(uid)
                .collection("Alerts")
                .document(category);

        statsRef.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) {
                Log.w(TAG, "Baseline stats missing");
                return;
            }

            Map<String, Object> catAvg =
                    (Map<String, Object>) snapshot.get("categoryAverages");

            if (catAvg == null || !catAvg.containsKey(category)) {
                Log.w(TAG, "No avg found for category: " + category);
                return;
            }

            double avg = ((Number) catAvg.get(category)).doubleValue();

            // SAME threshold logic (unchanged)
            double amountThreshold = Math.max(avg * 1.3, avg + 100);

            // Start of today timestamp (unchanged)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Timestamp startOfToday = new Timestamp(cal.getTime());

            db.collection("Users")
                    .document(uid)
                    .collection("Expenses")
                    .whereEqualTo("category", category)
                    .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                    .get()
                    .addOnSuccessListener(expensesSnapshot -> {

                        int todayCount = expensesSnapshot.size();
                        boolean isFrequencySpike = todayCount >= 5;

                        // 🔹 NEW: calculate TODAY TOTAL
                        double todayTotal = 0.0;
                        for (QueryDocumentSnapshot doc : expensesSnapshot) {
                            Double amt = doc.getDouble("amount");
                            if (amt != null) todayTotal += amt;
                        }

                        // 🔹 FIXED amount spike logic
                        boolean isAmountSpike = todayTotal >= amountThreshold;

                        if (!isAmountSpike && !isFrequencySpike) {
                            Log.d(TAG, "No anomaly detected for " + category);
                            return;
                        }

                        double finalTodayTotal = todayTotal;
                        alertRef.get().addOnSuccessListener(alertSnap -> {

                            // --- START OF RESET LOGIC FIX ---
                            boolean isOldAlertFromToday = false;
                            if (alertSnap.exists()) {
                                Timestamp lastAlertTimestamp = alertSnap.getTimestamp("timestamp");
                                if (lastAlertTimestamp != null) {
                                    Calendar calAlert = Calendar.getInstance();
                                    calAlert.setTime(lastAlertTimestamp.toDate());

                                    Calendar calNow = Calendar.getInstance();

                                    isOldAlertFromToday = (calAlert.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                                            calAlert.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR));
                                }
                            }

                            // Only fetch previous values if the alert found in DB is actually from TODAY
                            double prevAmount = (alertSnap.exists() && isOldAlertFromToday && alertSnap.contains("amount"))
                                    ? alertSnap.getDouble("amount") : 0;

                            int prevCount = (alertSnap.exists() && isOldAlertFromToday && alertSnap.contains("count"))
                                    ? alertSnap.getLong("count").intValue() : 0;
                            // --- END OF RESET LOGIC FIX ---

                            // SAME comparison logic, but with todayTotal
                            // If it's a new day, prevAmount/prevCount are 0, so this check passes and updates.
                            if (finalTodayTotal <= prevAmount && todayCount <= prevCount) return;

                            Map<String, Object> alertData = new HashMap<>();
                            alertData.put("type",
                                    isAmountSpike ? "Amount Spike" : "Frequency Spike");
                            alertData.put("category", category);

                            // 🔹 FIX: store TOTAL, not single expense
                            alertData.put("amount", finalTodayTotal);

                            alertData.put("count", todayCount);
                            alertData.put("timestamp", Timestamp.now());

                            alertRef.set(alertData)
                                    .addOnSuccessListener(v ->
                                            Log.d(TAG, "🚨 Alert written for " + category))
                                    .addOnFailureListener(e ->
                                            Log.e(TAG, "Alert write failed", e));
                        });
                    })
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Expense query failed", e));
        }).addOnFailureListener(e ->
                Log.e(TAG, "Stats fetch failed", e));
    }



}