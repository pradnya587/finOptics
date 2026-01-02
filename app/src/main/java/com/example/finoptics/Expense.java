package com.example.finoptics;

public class Expense {
    private double amount;
    private String category;
    private String note;

    public Expense() {} // Required for Firestore

    public double getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getNote() { return note; }
}