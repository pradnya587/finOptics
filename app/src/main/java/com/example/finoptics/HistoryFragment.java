package com.example.finoptics;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private EditText etSearchHistory;
    private ImageButton btnFilterHistory;

    private HistoryAdapter historyAdapter;

    // 🔹 Raw data from Firestore
    private final List<Expense> allExpenses = new ArrayList<>();

    // 🔹 Grouped data
    private List<ExpenseGroup> groupedExpenses = new ArrayList<>();

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public HistoryFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvHistory = view.findViewById(R.id.rvHistory);
        etSearchHistory = view.findViewById(R.id.etSearchHistory);
        btnFilterHistory = view.findViewById(R.id.btnFilterHistory);

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyAdapter = new HistoryAdapter(groupedExpenses);
        rvHistory.setAdapter(historyAdapter);

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        listenToFirestoreExpenses();

        // 🔍 Live Search
        etSearchHistory.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterExpenses(s.toString());
            }
        });

        btnFilterHistory.setOnClickListener(v -> {
            // TODO: Add filters later
        });
    }

    // ================= FIRESTORE =================

    private void listenToFirestoreExpenses() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        firestore.collection("Users")
                .document(uid)
                .collection("Expenses")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    allExpenses.clear();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        Expense expense = doc.toObject(Expense.class);
                        allExpenses.add(expense);
                    }

                    groupedExpenses = groupExpensesByMonth(allExpenses);
                    historyAdapter.updateList(groupedExpenses);
                });
    }

    // ================= GROUP BY MONTH =================
    private List<ExpenseGroup> groupExpensesByMonth(List<Expense> expenses) {

        Map<String, List<Expense>> map = new HashMap<>();

        // Step 1: Group raw expenses
        for (Expense e : expenses) {
            String month = e.getMonthYear(); // e.g. "January 2026"

            if (!map.containsKey(month)) {
                map.put(month, new ArrayList<>());
            }
            map.get(month).add(e);
        }

        // Step 2: Create ExpenseGroup objects
        List<ExpenseGroup> result = new ArrayList<>();
        for (String month : map.keySet()) {
            result.add(new ExpenseGroup(month, map.get(month)));
        }

        return result;
    }


    // ================= SEARCH =================

    private void filterExpenses(String query) {
        String cleanQuery = query.toLowerCase().trim();

        if (cleanQuery.isEmpty()) {
            groupedExpenses = groupExpensesByMonth(allExpenses);
            historyAdapter.updateList(groupedExpenses);
            return;
        }

        List<Expense> filtered = new ArrayList<>();
        String[] keywords = cleanQuery.split("\\s+");

        for (Expense e : allExpenses) {
            String content = (
                    e.getNote() + " " +
                            e.getCategory() + " " +
                            e.getAmount()
            ).toLowerCase();

            boolean matches = true;
            for (String word : keywords) {
                if (!content.contains(word)) {
                    matches = false;
                    break;
                }
            }

            if (matches) filtered.add(e);
        }

        groupedExpenses = groupExpensesByMonth(filtered);
        historyAdapter.updateList(groupedExpenses);
    }
}
