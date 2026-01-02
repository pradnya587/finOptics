package com.example.finoptics;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvUserGreeting, tvSpentValue, tvTotalBudgetLabel, tvUsedPercent, tvBudgetAmount;
    private ProgressBar circularProgressBar, progressTodayBar;
    private FloatingActionButton fabAddExpense;
    private RecyclerView rvRecentTransactions;

    // Adapter and List variables
    private TransactionAdapter adapter;
    private List<Expense> expenseList = new ArrayList<>();

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration monthlyListener, todayListener;

    private double monthlyBudget = 0;
    private final double DAILY_LIMIT = 500.0;

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI Components
        tvUserGreeting = view.findViewById(R.id.tvUserGreeting);
        tvSpentValue = view.findViewById(R.id.tvSpentValue);
        tvTotalBudgetLabel = view.findViewById(R.id.tvTotalBudgetLabel);
        tvUsedPercent = view.findViewById(R.id.tvUsedPercent);
        tvBudgetAmount = view.findViewById(R.id.tvBudgetAmount);
        circularProgressBar = view.findViewById(R.id.circularProgressBar);
        progressTodayBar = view.findViewById(R.id.progressTodayBar);
        fabAddExpense = view.findViewById(R.id.fabAddExpense);

        // Setup RecyclerView
        rvRecentTransactions = view.findViewById(R.id.rvRecentTransactions);
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentTransactions.setNestedScrollingEnabled(false); // Helps performance inside ScrollView

        // Initialize Adapter with the empty list
        adapter = new TransactionAdapter(expenseList);
        rvRecentTransactions.setAdapter(adapter);

        ImageButton btnLogout = view.findViewById(R.id.btnLogout);

        // Fetch User Data first, then start Syncing inside that method
        fetchUserData();

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            if (getActivity() != null) getActivity().finish();
        });

        fabAddExpense.setOnClickListener(v -> startActivity(new Intent(getActivity(), AddExpenseActivity.class)));

        return view;
    }

    private void fetchUserData() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("Users").document(mAuth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String username = document.getString("username");
                        Double budget = document.getDouble("monthly_budget");

                        if (username != null) {
                            tvUserGreeting.setText("Welcome back, " + username + " 👋");
                        }

                        if (budget != null && budget > 0) {
                            monthlyBudget = budget;
                            tvBudgetAmount.setText("₹" + String.format("%,.0f", budget));
                        } else {
                            monthlyBudget = 0;
                            tvBudgetAmount.setText("₹0");
                        }

                        // Critical: Start listeners AFTER budget is fetched
                        startMonthlySync();
                        startTodaySync();
                    }
                });
    }

    private void startMonthlySync() {
        if (mAuth.getCurrentUser() == null) return;

        monthlyListener = db.collection("Users").document(mAuth.getCurrentUser().getUid())
                .collection("Expenses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    expenseList.clear(); // Clear existing list to avoid duplicates
                    double totalSpent = 0;

                    for (QueryDocumentSnapshot doc : value) {
                        // Map Firestore doc to Expense model
                        Expense expense = doc.toObject(Expense.class);
                        expenseList.add(expense);

                        Double amt = doc.getDouble("amount");
                        if (amt != null) totalSpent += amt;
                    }

                    // Update UI components (Progress bar, texts)
                    updateMonthlyUI(totalSpent);

                    // Refresh the RecyclerView list
                    adapter.notifyDataSetChanged();
                });
    }

    private void startTodaySync() {
        if (mAuth.getCurrentUser() == null) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfToday = new Timestamp(cal.getTime());

        todayListener = db.collection("Users").document(mAuth.getCurrentUser().getUid())
                .collection("Expenses")
                .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    double todayTotal = 0;
                    for (QueryDocumentSnapshot doc : value) {
                        Double amt = doc.getDouble("amount");
                        if (amt != null) todayTotal += amt;
                    }
                    if (progressTodayBar != null) {
                        progressTodayBar.setMax((int) DAILY_LIMIT);
                        progressTodayBar.setProgress((int) Math.min(todayTotal, DAILY_LIMIT));
                    }
                });
    }

    private void updateMonthlyUI(double spent) {
        tvSpentValue.setText("₹" + String.format("%,.0f", spent));

        if (monthlyBudget > 0) {
            double remaining = monthlyBudget - spent;
            tvTotalBudgetLabel.setText("₹" + String.format("%,.0f", Math.max(0, remaining)) + " remaining");

            // Calculate and set percentage
            int percent = (int) ((spent / monthlyBudget) * 100);
            circularProgressBar.setProgress(Math.min(percent, 100));
            tvUsedPercent.setText(percent + "%");
        } else {
            // Default state if no budget is set
            tvTotalBudgetLabel.setText("₹0 remaining");
            circularProgressBar.setProgress(0);
            tvUsedPercent.setText("0%");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove listeners to prevent memory leaks
        if (monthlyListener != null) monthlyListener.remove();
        if (todayListener != null) todayListener.remove();
    }
}