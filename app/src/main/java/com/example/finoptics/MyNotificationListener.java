package com.example.finoptics;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyNotificationListener extends NotificationListenerService {

    private static final String TAG = "FinOptics_Notifier";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Allowed UPI / Bank apps
    private List<String> allowedApps = Arrays.asList(
            "com.google.android.apps.nbu.paisa.user", // GPay
            "com.phonepe.app",
            "com.paytm.app"
    );

    // Duplicate prevention
    private long lastTxnTime = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        String pkg = sbn.getPackageName();
        Log.d(TAG, "Notification received from: " + pkg);

        if (!allowedApps.contains(pkg)) return;

        // Prevent duplicate firing
        if (System.currentTimeMillis() - lastTxnTime < 3000) return;
        lastTxnTime = System.currentTimeMillis();

        Bundle extras = sbn.getNotification().extras;

        String title = extras.getString("android.title", "");
        CharSequence textSeq = extras.getCharSequence("android.text");
        String text = textSeq != null ? textSeq.toString() : "";

        Log.d(TAG, "TITLE: " + title);
        Log.d(TAG, "TEXT: " + text);

        processNotification(title, text);
    }

    private void processNotification(String title, String text) {

        String content = title + " " + text;

        String[] patterns = {
                "₹\\s?\\d+(\\.\\d+)?",
                "Rs\\.\\s?\\d+(\\.\\d+)?",
                "INR\\s?\\d+(\\.\\d+)?"
        };

        String amountFound = null;

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(content);
            if (m.find()) {
                amountFound = m.group();
                break;
            }
        }

        if (amountFound == null) {
            Log.d(TAG, "No amount found in notification");
            return;
        }

        double amount = parseAmount(amountFound);
        String txnType = detectTransactionType(content);
        String merchant = extractMerchant(content);

        Log.d(TAG, "Parsed -> Amount: " + amount + " Merchant: " + merchant + " Type: " + txnType);

        saveTransaction(amount, txnType, merchant, content);
    }

    private double parseAmount(String amountStr) {
        String clean = amountStr.replaceAll("[₹Rs.INR\\s,]", "");
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    private String detectTransactionType(String content) {
        String lower = content.toLowerCase();
        if (lower.contains("credited") || lower.contains("received")) return "income";
        return "expense";
    }

    private String extractMerchant(String content) {

        content = content.toLowerCase();

        if (content.contains("paid to ")) {
            return capitalize(content.split("paid to ")[1].split(" via|\\.")[0]);
        }
        if (content.contains("to ")) {
            return capitalize(content.split("to ")[1].split(" via|\\.")[0]);
        }
        if (content.contains("at ")) {
            return capitalize(content.split("at ")[1].split(" via|\\.")[0]);
        }

        return "Unknown";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    // 🔥 CRITICAL FIX: SharedPreferences UID
    private void saveTransaction(double amount, String txnType, String merchant, String content) {

        SharedPreferences prefs = getSharedPreferences("FinOptics", MODE_PRIVATE);
        String uid = prefs.getString("uid", null);

        if (uid == null) {
            Log.e(TAG, "UID missing — transaction NOT saved");
            return;
        }

        String category = autoCategorize(merchant + " " + content);

        Transaction transaction =
                new Transaction(amount, category, merchant, Timestamp.now());

        db.collection("Users")
                .document(uid)
                .collection("Expenses")
                .add(transaction)
                .addOnSuccessListener(doc -> {
                    Log.d(TAG, "AUTO TXN SAVED → ₹" + amount + " [" + category + "]");
                    detectTopAnomaly(uid, amount, category);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "AUTO TXN SAVE FAILED", e));
    }

    // ================= ANOMALY DETECTION =================

    private void detectTopAnomaly(String uid, double amount, String category) {

        DocumentReference statsRef = db.collection("Users")
                .document(uid)
                .collection("Stats")
                .document("baseline");

        DocumentReference alertRef = db.collection("Users")
                .document(uid)
                .collection("Alerts")
                .document(category);

        statsRef.get().addOnSuccessListener(snapshot -> {

            if (!snapshot.exists()) return;

            Map<String, Object> catAvg =
                    (Map<String, Object>) snapshot.get("categoryAverages");

            if (catAvg == null || !catAvg.containsKey(category)) return;

            double avg = ((Number) catAvg.get(category)).doubleValue();
            double threshold = Math.max(avg * 1.3, avg + 100);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Timestamp startOfToday = new Timestamp(cal.getTime());

            db.collection("Users").document(uid).collection("Expenses")
                    .whereEqualTo("category", category)
                    .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                    .get()
                    .addOnSuccessListener(snapshot2 -> {

                        int count = snapshot2.size();
                        double total = 0;

                        for (QueryDocumentSnapshot doc : snapshot2) {
                            Double amt = doc.getDouble("amount");
                            if (amt != null) total += amt;
                        }

                        if (total < threshold && count < 5) return;

                        Map<String, Object> alert = new HashMap<>();
                        alert.put("type", total >= threshold ? "Amount Spike" : "Frequency Spike");
                        alert.put("category", category);
                        alert.put("amount", total);
                        alert.put("count", count);
                        alert.put("timestamp", Timestamp.now());

                        alertRef.set(alert);
                    });
        });
    }

    // ================= AUTO CATEGORIZATION =================

    private String autoCategorize(String input) {

        String text = input.toLowerCase();

        Map<String, String> vendorMap = new HashMap<>();
        vendorMap.put("zomato", "Food");
        vendorMap.put("swiggy", "Food");
        vendorMap.put("amazon", "Shopping");
        vendorMap.put("flipkart", "Shopping");
        vendorMap.put("uber", "Transport");
        vendorMap.put("ola", "Transport");
        vendorMap.put("blinkit", "Shopping");

        for (String vendor : vendorMap.keySet()) {
            if (text.contains(vendor)) return vendorMap.get(vendor);
        }

        Map<String, List<String>> clusters = new HashMap<>();
        clusters.put("Food", Arrays.asList("pizza","burger","coffee","tea","dinner","lunch"));
        clusters.put("Transport", Arrays.asList("fuel","petrol","cab","bus","metro","auto"));
        clusters.put("Health", Arrays.asList("doctor","medicine","hospital","gym"));
        clusters.put("Bills", Arrays.asList("electricity","rent","wifi","recharge","gas"));

        for (Map.Entry<String,List<String>> entry : clusters.entrySet()) {
            for (String w : entry.getValue()) {
                if (text.contains(w)) return entry.getKey();
            }
        }

        return "Other";
    }
}