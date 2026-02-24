package com.example.finoptics;

import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SpendingCalendarActivity extends AppCompatActivity {

    private GridLayout calendarGrid;
    private TextView tvMonthYear;
    private ImageView btnPrevMonth, btnNextMonth, btnBack;

    private Calendar currentMonth;
    private FirebaseFirestore db;
    private String userId;

    private final Map<String, Double> dailyTotals = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spending_calendar);

        calendarGrid = findViewById(R.id.calendarGrid);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        btnBack = findViewById(R.id.btnBack);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        currentMonth = Calendar.getInstance();

        btnBack.setOnClickListener(v -> finish());
        btnPrevMonth.setOnClickListener(v -> changeMonth(-1));
        btnNextMonth.setOnClickListener(v -> changeMonth(1));

        loadMonth();
    }

    private void changeMonth(int offset) {
        currentMonth.add(Calendar.MONTH, offset);
        loadMonth();
    }

    private void loadMonth() {
        dailyTotals.clear();
        calendarGrid.removeAllViews();

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentMonth.getTime()));

        Calendar start = (Calendar) currentMonth.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);

        fetchDailyTotals(start.getTimeInMillis(), end.getTimeInMillis());
    }

    private void fetchDailyTotals(long start, long end) {

        db.collection("Users")
                .document(userId)
                .collection("Expenses")
                .get()
                .addOnSuccessListener(snapshot -> {

                    for (QueryDocumentSnapshot doc : snapshot) {
                        Timestamp ts = doc.getTimestamp("timestamp");
                        Double amt = doc.getDouble("amount");

                        if (ts == null || amt == null) continue;

                        long time = ts.toDate().getTime();
                        if (time < start || time >= end) continue;

                        Calendar c = Calendar.getInstance();
                        c.setTimeInMillis(time);
                        String key = c.get(Calendar.DAY_OF_MONTH) + "";

                        dailyTotals.put(key,
                                dailyTotals.getOrDefault(key, 0.0) + amt);
                    }

                    renderCalendar();
                });
    }

    private void renderCalendar() {
        Calendar temp = (Calendar) currentMonth.clone();
        temp.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = temp.getActualMaximum(Calendar.DAY_OF_MONTH);

        double max = 1;
        for (double v : dailyTotals.values()) max = Math.max(max, v);

        for (int i = 0; i < firstDayOfWeek; i++) {
            addEmptyCell();
        }

        for (int day = 1; day <= daysInMonth; day++) {
            double amount = dailyTotals.getOrDefault(String.valueOf(day), 0.0);
            addDayCell(day, amount, max);
        }
    }

    private void addEmptyCell() {
        View v = new View(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 110;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        v.setLayoutParams(params);
        calendarGrid.addView(v);
    }

    private void addDayCell(int day, double amount, double max) {

        TextView tv = new TextView(this);
        tv.setText(String.valueOf(day));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setGravity(android.view.Gravity.CENTER);

        double intensity = amount / max;
        tv.setBackgroundColor(getHeatColor(intensity));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 110;
        params.setMargins(6, 6, 6, 6);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

        tv.setLayoutParams(params);

        calendarGrid.addView(tv);
    }

    private int getHeatColor(double intensity) {
        if (intensity == 0) return 0xFF1E293B;
        if (intensity < 0.25) return 0xFF1F6FEB;
        if (intensity < 0.5) return 0xFF22D3A6;
        if (intensity < 0.75) return 0xFFFACC15;
        return 0xFFEF4444;
    }
}
