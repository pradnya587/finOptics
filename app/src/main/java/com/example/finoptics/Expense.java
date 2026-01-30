package com.example.finoptics;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Expense {

    private String category;     // required
    private int amount;          // required
    private String note;         // required, main description
    private Timestamp timestamp; // required (Firestore)

    // 🔴 Required by Firestore
    public Expense() {}

    // Optional constructor for manual creation
    public Expense(String category, int amount, String note, Timestamp timestamp) {
        this.category = category;
        this.amount = amount;
        this.note = note;
        this.timestamp = timestamp;
    }

    // ---------- 🔥 FIRESTORE SAFE SETTERS (ADDED) ----------

    // Firestore sends numbers as Long
    public void setAmount(Long amount) {
        if (amount != null) {
            this.amount = amount.intValue();
        }
    }

    // Optional: defensive setters (future-proof)
    public void setCategory(String category) {
        this.category = category;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    // ---------- Getters (Firestore uses these) ----------

    public String getCategory() {
        return category;
    }

    public int getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    // ---------- Derived helpers for UI ----------

    /** Returns date as "MMMM dd, yyyy" e.g., March 10, 2026 */
    public String getDate() {
        if (timestamp == null) return "";
        return formatTimestamp("MMMM dd, yyyy");
    }

    /** Returns time as "hh:mm a" e.g., 08:30 AM */
    public String getTime() {
        if (timestamp == null) return "";
        return formatTimestamp("hh:mm a");
    }

    /** Returns month + year as "MMMM yyyy" e.g., March 2026 */
    public String getMonthYear() {
        if (timestamp == null) return "";
        return formatTimestamp("MMMM yyyy");
    }

    // ---------- Private helper ----------

    private String formatTimestamp(String pattern) {
        Date date = timestamp.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(date);
    }
}
