package com.example.finoptics;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
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

    /* -------------------- LOCAL DATA -------------------- */

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

        // ✅ Makeup belongs to Shopping
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

    /* -------------------- CORE FLOW -------------------- */

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

    /* -------------------- GEMINI CALL -------------------- */

    private void callGeminiAI(String input, double fallbackAmount) {
        progressBar.setVisibility(View.VISIBLE);
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
                            progressBar.setVisibility(View.GONE);
                            btnSave.setEnabled(true);
                        }
                    });
                }
            });

        } catch (Exception e) {
            fallbackSave(input, fallbackAmount);
            progressBar.setVisibility(View.GONE);
            btnSave.setEnabled(true);
        }
    }

    private void fallbackSave(String input, double amount) {
        saveExpense(amount, "Other", input);
    }

    /* -------------------- HELPERS -------------------- */

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

    /* -------------------- FIREBASE -------------------- */

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
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Save failed",
                                Toast.LENGTH_SHORT).show());
    }
}
