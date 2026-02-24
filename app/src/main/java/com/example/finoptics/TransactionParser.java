package com.example.finoptics;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    private static final String TAG = "FinOptics_Parser";

    public static void processTransaction(Context context, String content) {
        if (content == null || content.isEmpty()) return;

        Log.d(TAG, "Parsing: " + content);

        double amount = parseAmount(content);
        String txnType = detectTransactionType(content);
        String merchant = extractMerchant(content);
        String category = autoCategorize(context, content);

        saveTransaction(context, amount, txnType, merchant, content, category);
    }

    private static double parseAmount(String content) {
        String[] patterns = {
                "₹\\s?[\\d,]+(\\.\\d{1,2})?",
                "Rs\\.\\s?[\\d,]+(\\.\\d{1,2})?",
                "INR\\s?[\\d,]+(\\.\\d{1,2})?"
        };

        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(content);
            if (m.find()) {
                String clean = m.group().replaceAll("[₹Rs.INR\\s,]", "");
                try { return Double.parseDouble(clean); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static String detectTransactionType(String content) {
        String lower = content.toLowerCase();
        if (lower.contains("credited") || lower.contains("received")) return "income";
        return "expense";
    }

    private static String extractMerchant(String content) {
        String lower = content.toLowerCase();
        try {
            if (lower.contains("at ")) return capitalize(lower.split("at ")[1].split(" via| on|\\.")[0]);
            if (lower.contains("to ")) return capitalize(lower.split("to ")[1].split(" via|\\.")[0]);
            if (lower.contains("paid to ")) return capitalize(lower.split("paid to ")[1].split(" via|\\.")[0]);
        } catch (Exception e) {
            Log.e(TAG, "Merchant parsing failed", e);
        }
        return "Unknown Merchant";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    private static String autoCategorize(Context context, String input) {
        String text = input.toLowerCase();

        // Predefined vendors
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

        // Keyword clusters
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

        // AI/learned keywords
        SharedPreferences prefs = context.getSharedPreferences("AI_Learning", Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (text.contains(e.getKey())) return (String) e.getValue();
        }

        return "Other";
    }

    private static void saveTransaction(Context context, double amount, String txnType,
                                        String merchant, String content, String category) {
        SharedPreferences prefs = context.getSharedPreferences("FinOptics", Context.MODE_PRIVATE);
        String uid = prefs.getString("uid", null);
        if (uid == null) { Log.e(TAG, "UID missing"); return; }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 🔹 Simple duplicate prevention by checking today’s transactions
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        Timestamp startOfToday = new Timestamp(cal.getTime());

        db.collection("Users").document(uid).collection("Expenses")
                .whereGreaterThanOrEqualTo("timestamp", startOfToday)
                .get().addOnSuccessListener(snapshot -> {

                    boolean duplicate = false;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Double amt = doc.getDouble("amount");
                        String mer = doc.getString("note");
                        if (amt != null && amt == amount && mer != null && mer.contains(merchant)) {
                            duplicate = true;
                            break;
                        }
                    }

                    if (!duplicate) {
                        Map<String,Object> txn = new HashMap<>();
                        txn.put("amount", amount);
                        txn.put("category", category);
                        txn.put("note", merchant + " | " + content);
                        txn.put("timestamp", Timestamp.now());

                        db.collection("Users").document(uid)
                                .collection("Expenses")
                                .add(txn)
                                .addOnSuccessListener(d -> Log.d(TAG, "Saved: " + amount + " → " + category))
                                .addOnFailureListener(e -> Log.e(TAG, "Save failed", e));
                    } else {
                        Log.d(TAG, "Duplicate detected, skipping");
                    }
                });
    }
}