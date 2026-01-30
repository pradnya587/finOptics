package com.example.finoptics;

import java.util.List;

public class ExpenseGroup {
    private String month;
    private List<Expense> expenses;

    public ExpenseGroup(String month, List<Expense> expenses) {
        this.month = month;
        this.expenses = expenses;
    }

    public String getMonth() { return month; }
    public List<Expense> getExpenses() { return expenses; }
}
