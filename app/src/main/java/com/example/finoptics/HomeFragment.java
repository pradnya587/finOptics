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
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// 🔹 NEW imports (safe)
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


import android.util.Log;

public class HomeFragment extends Fragment {

    private TextView tvUserGreeting, tvSpentValue, tvTotalBudgetLabel, tvUsedPercent, tvBudgetAmount, tvViewAll;
    private ProgressBar circularProgressBar, progressTodayBar;
    private FloatingActionButton fabAddExpense;
    private RecyclerView rvRecentTransactions;

    private TransactionAdapter adapter;
    private List<Expense> expenseList = new ArrayList<>();

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration monthlyStatsListener, recentTransactionsListener, todayListener, alertListener;

    private double monthlyBudget = 0;
    private final double DAILY_LIMIT = 500.0;

    // 🔹 ALERT UI
    private View cardTopAlert;
    private TextView tvAlertTitle, tvAlertMessage;

    private TextView tvTodayStatus, tvDailyLimitLabel;

    // Top of HomeFragment.java

    private TextView tvPredictiveInsight; // 🔹 ADD THIS LINE

    // Near the top of HomeFragment.java


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
        tvViewAll = view.findViewById(R.id.tvViewAll);
        circularProgressBar = view.findViewById(R.id.circularProgressBar);
        progressTodayBar = view.findViewById(R.id.progressTodayBar);
        fabAddExpense = view.findViewById(R.id.fabAddExpense);

        // ALERT UI
        cardTopAlert = view.findViewById(R.id.cardTopAlert);
        tvAlertTitle = view.findViewById(R.id.tvAlertTitle);
        tvAlertMessage = view.findViewById(R.id.tvAlertMessage);

        tvTodayStatus = view.findViewById(R.id.tvTodayStatus);
        tvDailyLimitLabel = view.findViewById(R.id.tvDailyLimitLabel);

        tvPredictiveInsight = view.findViewById(R.id.tvPredictiveInsight); // 🔹 ADD THIS LINE

        // Setup RecyclerView
        rvRecentTransactions = view.findViewById(R.id.rvRecentTransactions);
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentTransactions.setNestedScrollingEnabled(false);

        adapter = new TransactionAdapter(expenseList);
        rvRecentTransactions.setAdapter(adapter);

        ImageButton btnLogout = view.findViewById(R.id.btnLogout);

        fetchUserData(); // unchanged
        fetchTopAlert(); // 🔹 NEW: load top alert

        tvViewAll.setOnClickListener(v ->
                Toast.makeText(getContext(), "Opening full history...", Toast.LENGTH_SHORT).show()
        );

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            if (getActivity() != null) getActivity().finish();
        });

        fabAddExpense.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AddExpenseActivity.class))
        );

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
                        }

                        // Existing logic
                        startMonthlySync();
                        startTodaySync();

                        // 🧠 NEW: trigger baseline learning (safe)
                        callComputeBaseline();
                    }
                });
    }

    // 🔹 NEW METHOD (does NOT affect existing features)
    private void callComputeBaseline() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        user.getIdToken(true).addOnSuccessListener(result -> {
            String idToken = result.getToken();

            new Thread(() -> {
                try {
                    URL url = new URL(
                            "https://us-central1-finoptics-79357.cloudfunctions.net/computeBaseline"
                    );

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Authorization", "Bearer " + idToken);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    os.write(new byte[0]);
                    os.close();

                    conn.getResponseCode(); // fire-and-forget
                    conn.disconnect();

                } catch (Exception ignored) {}
            }).start();
        });
    }

    private void startMonthlySync() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 🔹 Added start of month calculation for the reset
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        com.google.firebase.Timestamp startOfMonth = new com.google.firebase.Timestamp(cal.getTime());

        // 🔹 Preserved your listener exactly, adding only the .whereGreaterThanOrEqualTo filter
        monthlyStatsListener = db.collection("Users").document(uid)
                .collection("Expenses")
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    double totalSpent = 0;
                    for (QueryDocumentSnapshot doc : value) {
                        Double amt = doc.getDouble("amount");
                        if (amt != null) totalSpent += amt;
                    }
                    updateMonthlyUI(totalSpent);
                });

        recentTransactionsListener = db.collection("Users").document(uid)
                .collection("Expenses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    expenseList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        expenseList.add(doc.toObject(Expense.class));
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void startTodaySync() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 1. Setup Timeframes
        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int daysRemaining = (daysInMonth - currentDay) + 1; // Includes today

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfToday = new Timestamp(cal.getTime());

        // 2. Fetch Monthly Progress to calculate Dynamic Limit
        db.collection("Users").document(uid).get().addOnSuccessListener(userDoc -> {
            double budget = userDoc.contains("monthly_budget") ? userDoc.getDouble("monthly_budget") : 0;

            // Fetch total spent this month to find remaining balance
            cal.set(Calendar.DAY_OF_MONTH, 1);
            Timestamp startOfMonth = new Timestamp(cal.getTime());

            db.collection("Users").document(uid).collection("Expenses")
                    .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        double spentThisMonth = 0;
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            spentThisMonth += doc.getDouble("amount");
                        }

                        // 🔹 CALCULATE STS: (Remaining Budget / Days Left)
                        double remainingBudget = Math.max(0, budget - spentThisMonth);
                        double dynamicDailyLimit = remainingBudget / daysRemaining;

                        // 3. Original Today Sync Logic with Dynamic Limit
                        todayListener = db.collection("Users").document(uid)
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
                                        progressTodayBar.setMax((int) dynamicDailyLimit);
                                        progressTodayBar.setProgress((int) Math.min(todayTotal, dynamicDailyLimit));
                                    }

                                    if (tvTodayStatus != null) {
                                        if (todayTotal >= dynamicDailyLimit) {
                                            tvTodayStatus.setText("Over limit");
                                            tvTodayStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                                        } else {
                                            tvTodayStatus.setText("On track");
                                            tvTodayStatus.setTextColor(android.graphics.Color.parseColor("#00E676"));
                                        }
                                    }
                                });
                    });
        });
    }

    private void updateMonthlyUI(double spent) {
        tvSpentValue.setText("₹" + String.format("%,.0f", spent));

        updatePredictiveInsight(spent);

        if (monthlyBudget > 0) {
            double remaining = monthlyBudget - spent;

            // 🔹 Preserved your label logic, adding a color change for the "over budget" state
            if (spent > monthlyBudget) {
                tvTotalBudgetLabel.setText("₹" + String.format("%,.0f", spent - monthlyBudget) + " over budget");
                tvTotalBudgetLabel.setTextColor(android.graphics.Color.parseColor("#FF5252")); // Red for exceeding
            } else {
                tvTotalBudgetLabel.setText("₹" + String.format("%,.0f", Math.max(0, remaining)) + " remaining");
                tvTotalBudgetLabel.setTextColor(android.graphics.Color.parseColor("#80FFFFFF")); // Your original faded white
            }

            int percent = (int) ((spent / monthlyBudget) * 100);
            circularProgressBar.setProgress(Math.min(percent, 100));
            tvUsedPercent.setText(percent + "%");

            // 🔹 Setting percentage text to red if 100% or more
            if (percent >= 100) {
                tvUsedPercent.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            } else {
                tvUsedPercent.setTextColor(android.graphics.Color.WHITE);
            }
        }
    }

    // 🔹 NEW: Fetch top alert and display
    private void fetchTopAlert() {

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        alertListener = db.collection("Users")
                .document(uid)
                .collection("Alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((value, error) -> {

                    if (error != null) {
                        Log.e("HomeFragment", "Error fetching alerts: ", error);
                        cardTopAlert.setVisibility(View.GONE);
                        return;
                    }

                    if (value == null || value.isEmpty()) {
                        Log.d("HomeFragment", "No alerts found.");
                        cardTopAlert.setVisibility(View.GONE);
                        return;
                    }

                    Log.d("HomeFragment", "Alerts fetched: " + value.size());

                    for (QueryDocumentSnapshot doc : value) {

                        Log.d("HomeFragment", "Alert doc data: " + doc.getData());

                        // 🔹 START OF DATE VALIDATION
                        Timestamp alertTimestamp = doc.getTimestamp("timestamp");
                        if (alertTimestamp != null) {
                            Calendar calAlert = Calendar.getInstance();
                            calAlert.setTime(alertTimestamp.toDate());

                            Calendar calNow = Calendar.getInstance();

                            boolean isSameDay = (calAlert.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                                    calAlert.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR));

                            if (!isSameDay) {
                                Log.d("HomeFragment", "Old alert detected. Hiding UI.");
                                cardTopAlert.setVisibility(View.GONE);
                                return;
                            }
                        }
                        // 🔹 END OF DATE VALIDATION

                        String type = doc.getString("type");
                        Double amount = doc.getDouble("amount");
                        String category = doc.getString("category");

                        if (type == null || amount == null || category == null) {
                            Log.w("HomeFragment", "Alert missing required fields!");
                            cardTopAlert.setVisibility(View.GONE);
                            return;
                        }

                        String title = type; // "Amount Spike" / "Frequency Spike"

                        // 🔹 FIXED semantic message
                        String message =
                                "Your total spending on " + category +
                                        " today is ₹" + amount.intValue();

                        cardTopAlert.setVisibility(View.VISIBLE);
                        tvAlertTitle.setText(title);
                        tvAlertMessage.setText(message);
                    }
                });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (monthlyStatsListener != null) monthlyStatsListener.remove();
        if (recentTransactionsListener != null) recentTransactionsListener.remove();
        if (todayListener != null) todayListener.remove();
        if (alertListener != null) alertListener.remove();
    }

    // 🔹 Add this new helper method at the bottom of your class
    private void updatePredictiveInsight(double spent) {
        if (monthlyBudget <= 0 || spent <= 0) return;

        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysRemaining = (daysInMonth - currentDay) + 1;

        // Calculate Velocity (Avg spend per day)
        double dailyVelocity = spent / currentDay;

        // Calculate Runway (How many days money will last)
        double remainingMoney = monthlyBudget - spent;
        double daysOfRunwayLeft = remainingMoney / dailyVelocity;

        if (tvPredictiveInsight != null) {
            if (remainingMoney <= 0) {
                tvPredictiveInsight.setText("Budget exhausted for this month.");
                tvPredictiveInsight.setTextColor(android.graphics.Color.parseColor("#FF5252")); // Red
            } else if (daysOfRunwayLeft < daysRemaining) {
                int depletionDay = currentDay + (int) daysOfRunwayLeft;
                tvPredictiveInsight.setText("Careful: Budget may run out by day " + depletionDay);
                tvPredictiveInsight.setTextColor(android.graphics.Color.parseColor("#FFB74D")); // Orange
            } else {
                tvPredictiveInsight.setText("On track to last until end of month!");
                tvPredictiveInsight.setTextColor(android.graphics.Color.parseColor("#22D3A6")); // Green
            }
        }
    }

}
