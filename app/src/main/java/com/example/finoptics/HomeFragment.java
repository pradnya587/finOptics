package com.example.finoptics;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment {

    private TextView tvUserGreeting, tvSpentValue, tvTotalBudgetLabel;
    private ProgressBar circularProgressBar;
    private FloatingActionButton fabAddExpense;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind all UI components from your new professional layout
        tvUserGreeting = view.findViewById(R.id.tvUserGreeting);
        tvSpentValue = view.findViewById(R.id.tvSpentValue);
        tvTotalBudgetLabel = view.findViewById(R.id.tvTotalBudgetLabel);
        circularProgressBar = view.findViewById(R.id.circularProgressBar);
        fabAddExpense = view.findViewById(R.id.fabAddExpense);
        ImageButton btnLogout = view.findViewById(R.id.btnLogout);

        fetchUserData();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) getActivity().finish();
        });

        // This will open the expense entry later
        fabAddExpense.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Adding new expense...", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void fetchUserData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("Users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String username = document.getString("username");
                        Double budget = document.getDouble("monthly_budget");

                        if (username != null) tvUserGreeting.setText("Hello, " + username);

                        if (budget != null) {
                            // For now, spent is 0, but the UI needs to reflect the goal
                            tvSpentValue.setText("₹0");
                            tvTotalBudgetLabel.setText("of ₹" + String.format("%.0f", budget) + " budget");

                            // To show the teal ring properly like the inspiration:
                            circularProgressBar.setMax(100);
                            circularProgressBar.setProgress(0); // 0% spent = empty ring
                        }
                    }
                });
    }
}