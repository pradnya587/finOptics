package com.example.finoptics;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class SetupActivity extends AppCompatActivity {

    EditText etBudget;
    Button btnSave;
    TextView tvSkip;
    FirebaseFirestore db;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Link UI Elements
        etBudget = findViewById(R.id.etMonthlyBudget);
        btnSave = findViewById(R.id.btnSaveBudget);
        tvSkip = findViewById(R.id.tvSkipSetup);

        // Button Listeners
        btnSave.setOnClickListener(v -> saveBudgetToFirestore());

        // Skip allows them to go to Home without setting budget
        tvSkip.setOnClickListener(v -> moveToHome());
    }

    private void saveBudgetToFirestore() {
        String budgetStr = etBudget.getText().toString().trim();

        if (TextUtils.isEmpty(budgetStr)) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double budget = Double.parseDouble(budgetStr);

            if (mAuth.getCurrentUser() != null) {
                String userId = mAuth.getCurrentUser().getUid();

                // Store budget in Firestore
                Map<String, Object> userData = new HashMap<>();
                userData.put("monthly_budget", budget);

                // We change SuccessListener to OnCompleteListener to ensure navigation triggers
                db.collection("Users").document(userId)
                        .set(userData, SetOptions.merge())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(SetupActivity.this, "Budget Set!", Toast.LENGTH_SHORT).show();
                            } else {
                                // Inform user but don't block them; Firebase will sync when online
                                Toast.makeText(SetupActivity.this, "Syncing budget in background...", Toast.LENGTH_SHORT).show();
                            }

                            // CRITICAL FIX: Move to home immediately after the task finishes
                            moveToHome();
                        });
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveToHome() {
        // PRESERVING YOUR LOGIC: Clear backstack so they can't go back to Setup
        Intent intent = new Intent(SetupActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}