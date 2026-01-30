package com.example.finoptics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<Expense> expenseList;

    public TransactionAdapter(List<Expense> expenseList) {
        this.expenseList = expenseList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Expense expense = expenseList.get(position);

        holder.tvNote.setText(expense.getNote());
        holder.tvCategory.setText(expense.getCategory());
        holder.tvAmount.setText("₹" + expense.getAmount());

        // Dynamically set the icon based on the category string
        int iconRes = getCategoryIcon(expense.getCategory());
        holder.ivCategoryIcon.setImageResource(iconRes);
    }

    /**
     * Helper method to map category names to drawable resources.
     * Ensure these .xml or .png files exist in your res/drawable folder!
     */
    private int getCategoryIcon(String category) {
        if (category == null) return R.drawable.ic_food; // Default fallback

        switch (category.toLowerCase().trim()) {
            case "food":
            case "dining":
                return R.drawable.ic_food;
            case "health":
            case "medical":
                return R.drawable.ic_health;
            case "entertainment":
            case "movies":
            case "leisure":
                return R.drawable.ic_entertainment;
            case "shopping":
                return R.drawable.ic_shopping;
            case "transport":
            case "travel":
            case "fuel":
                return R.drawable.ic_transport;
            default:
                return R.drawable.ic_general; // Or any generic icon you have
        }
    }

    @Override
    public int getItemCount() {
        return expenseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote, tvCategory, tvAmount;
        ImageView ivCategoryIcon; // Added this to match your XML

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNote = itemView.findViewById(R.id.tvTransactionNote);
            tvCategory = itemView.findViewById(R.id.tvTransactionCategory);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon); // Link to your ImageView
        }
    }
}