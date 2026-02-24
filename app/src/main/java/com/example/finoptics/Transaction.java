package com.example.finoptics;

import com.google.firebase.Timestamp;

public class Transaction {

    private double amount;
    private String category;
    private String note;
    private Timestamp timestamp;

    // Default constructor required for Firestore
    public Transaction() {}

    // Full constructor
    public Transaction(double amount, String category, String note, Timestamp timestamp) {
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    // Optional convenience for display
    public String displayAmount() {
        return "₹" + (int) amount;
    }
}
