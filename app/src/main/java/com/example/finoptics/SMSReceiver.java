package com.example.finoptics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.content.SharedPreferences;

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

public class SMSReceiver extends BroadcastReceiver {

    private static final String TAG = "FinOptics_SMSReceiver";
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private List<String> allowedSenders = Arrays.asList(
            "VM-GPAYBNK", "VM-PAYTM", "VM-PHONEPE", "VM-ICICIB", "VM-HDFCBK"
    );

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");

        if (pdus == null) return;

        for (Object pdu : pdus) {

            SmsMessage sms;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }

            String sender = sms.getDisplayOriginatingAddress();
            String message = sms.getMessageBody();

            Log.d(TAG, "SMS received from: " + sender + " -> " + message);

            // if (!allowedSenders.contains(sender)) continue;

            processSMS(context, message);
        }
    }

    private void processSMS(Context context, String content) {

        String lowerContent = content.toLowerCase();

        // 1. Extract amount
        Pattern p = Pattern.compile("(?:rs\\.?|inr|₹)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)");
        Matcher m = p.matcher(lowerContent);

        if (!m.find()) {
            Log.d(TAG, "No amount found — ignored");
            return;
        }

        double amount = parseAmount(m.group(1));

        // 2. STRICT income detection
        if (isIncomingTransaction(lowerContent)) {
            Log.d(TAG, "Incoming transaction ignored");
            return;
        }

        String merchant = extractMerchantFromSMS(lowerContent);

        Log.d(TAG, "Parsed -> ₹" + amount + " Merchant: " + merchant);

        saveTransaction(context, amount, "DEBIT", merchant, content);
    }

    // 🔐 VERY STRICT CREDIT FILTER
    private boolean isIncomingTransaction(String content) {

        content = content.toLowerCase();

        boolean incoming =
                content.contains("credited") ||
                        content.contains("received") ||
                        content.contains("refund") ||
                        content.contains("cashback") ||
                        content.contains("reversal");

        boolean outgoing =
                content.contains("debit") ||
                        content.contains("debited") ||
                        content.contains("spent") ||
                        content.contains("paid") ||
                        content.contains("purchase") ||
                        content.contains("sent") ||
                        content.contains("withdrawn") ||
                        content.contains("transfer");

        // Incoming only if no outgoing keyword exists
        return incoming && !outgoing;
    }

    private double parseAmount(String amountStr) {
        try {
            return Double.parseDouble(amountStr.replace(",", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractMerchantFromSMS(String content) {
        try {
            if (content.contains(" to ")) {
                return capitalize(content.split(" to ")[1].split(" via| on|\\.")[0].trim());
            } else if (content.contains(" at ")) {
                return capitalize(content.split(" at ")[1].split(" via| on|\\.")[0].trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Merchant parse failed", e);
        }
        return "Unknown Merchant";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void saveTransaction(Context context, double amount, String txnType,
                                 String merchant, String content) {

        SharedPreferences prefs =
                context.getSharedPreferences("FinOptics", Context.MODE_PRIVATE);

        String uid = prefs.getString("uid", null);

        if (uid == null) {
            Log.e(TAG, "UID missing — transaction NOT saved");
            return;
        }

        String category = autoCategorize(content + " " + merchant);

        Transaction transaction =
                new Transaction(amount, category, merchant, Timestamp.now());

        db.collection("Users")
                .document(uid)
                .collection("Expenses")
                .add(transaction)
                .addOnSuccessListener(doc -> {
                    Log.d(TAG, "✅ TXN SAVED → ₹" + amount + " [" + category + "]");
                    detectTopAnomaly(uid, amount, category);
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ TXN SAVE FAILED", e));
    }

    /* ==================== ANOMALY DETECTION ==================== */

    private void detectTopAnomaly(String uid, double amount, String category) {

        DocumentReference statsRef =
                db.collection("Users").document(uid)
                        .collection("Stats").document("baseline");

        DocumentReference alertRef =
                db.collection("Users").document(uid)
                        .collection("Alerts").document(category);

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

            db.collection("Users").document(uid)
                    .collection("Expenses")
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

    /* ==================== AUTO-CATEGORIZATION ==================== */

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
        clusters.put("Food", Arrays.asList("pizza", "burger", "coffee", "tea", "dinner", "lunch"));
        clusters.put("Transport", Arrays.asList("fuel", "petrol", "cab", "bus", "metro", "auto"));
        clusters.put("Health", Arrays.asList("doctor", "medicine", "hospital", "gym"));
        clusters.put("Bills", Arrays.asList("electricity", "rent", "wifi", "recharge", "gas"));

        for (Map.Entry<String, List<String>> entry : clusters.entrySet()) {
            for (String w : entry.getValue()) {
                if (text.contains(w)) return entry.getKey();
            }
        }

        return "Other";
    }
}