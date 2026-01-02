package com.example.finoptics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        holder.tvAmount.setText("₹" + String.format("%.0f", expense.getAmount()));
    }

    @Override
    public int getItemCount() {
        return expenseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote, tvCategory, tvAmount;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNote = itemView.findViewById(R.id.tvTransactionNote);
            tvCategory = itemView.findViewById(R.id.tvTransactionCategory);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
        }
    }
}