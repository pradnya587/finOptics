package com.example.finoptics;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat; // Added for compatibility

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText etSmartInput;
    private Button btnSave;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private Map<String, String> localVendorMap = new HashMap<>();
    private Map<String, List<String>> keywordClusters = new HashMap<>();
    private GenerativeModelFutures model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etSmartInput = findViewById(R.id.etSmartInput);
        btnSave = findViewById(R.id.btnSaveExpense);
        progressBar = findViewById(R.id.progressBar);

        // API Key safety: In a real interview, mention using local.properties
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", "YOUR_API_KEY");
        model = GenerativeModelFutures.from(gm);

        initLocalData();
        btnSave.setOnClickListener(v -> startSmartCategorization());
    }

    private void initLocalData() {
        localVendorMap.put("starbucks", "Food");
        localVendorMap.put("uber", "Transport");
        keywordClusters.put("Food", Arrays.asList("coffee", "tea", "pizza", "rice", "burger", "boba"));
        keywordClusters.put("Transport", Arrays.asList("petrol", "fuel", "bus", "metro", "parking"));
    }

    private void startSmartCategorization() {
        String input = etSmartInput.getText().toString().trim();
        if (input.isEmpty()) return;

        double localAmount = extractAmount(input);
        String localCategory = autoCategorize(input.toLowerCase());

        if (!localCategory.equals("Other") && localAmount > 0) {
            saveExpense(localAmount, localCategory, input);
        } else {
            callGeminiAI(input, localAmount);
        }
    }

    private void callGeminiAI(String input, double localAmount) {
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        String prompt = "Extract data from: '" + input + "'. " +
                "You MUST categorize this into one of these exact words: " +
                "Food, Transport, Bills, Shopping, Health, Entertainment, or Personal. " + // Added Health and Personal
                "Return ONLY JSON: {\"amount\": 0.0, \"category\": \"\", \"note\": \"\"}";

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    try {
                        String jsonStr = result.getText().replace("```json", "").replace("```", "").trim();
                        JSONObject json = new JSONObject(jsonStr);

                        double aiAmount = json.optDouble("amount", localAmount);
                        String aiCategory = json.optString("category", "Other");
                        String aiNote = json.optString("note", input);

                        saveExpense(aiAmount, aiCategory, aiNote);

                    } catch (Exception e) {
                        saveExpense(localAmount, "Other", input);
                    } finally {
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(AddExpenseActivity.this, "AI Offline. Saving locally.", Toast.LENGTH_SHORT).show();
                    saveExpense(localAmount, "Other", input);
                });
            }
            // FIX: ContextCompat ensures this works on API 24+ without crashing
        }, ContextCompat.getMainExecutor(this));
    }

    private double extractAmount(String input) {
        Matcher m = Pattern.compile("(\\d+(\\.\\d+)?)").matcher(input);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    private String autoCategorize(String note) {
        for (String vendor : localVendorMap.keySet()) {
            if (note.contains(vendor)) return localVendorMap.get(vendor);
        }
        for (Map.Entry<String, List<String>> entry : keywordClusters.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (note.contains(keyword)) return entry.getKey();
            }
        }
        return "Other";
    }

    private void saveExpense(double amount, String category, String note) {
        String userId = mAuth.getCurrentUser().getUid();
        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("note", note);
        data.put("category", category);
        data.put("timestamp", Timestamp.now());

        db.collection("Users").document(userId).collection("Expenses").add(data)
                .addOnSuccessListener(doc -> {
                    Toast.makeText(this, "Smart Added: " + category, Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
}