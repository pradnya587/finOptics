package com.example.finoptics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<ExpenseGroup> expenseGroups;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public HistoryAdapter(List<ExpenseGroup> expenseGroups) {
        this.expenseGroups = expenseGroups;
    }

    // ---------------- ITEM TYPE ----------------
    @Override
    public int getItemViewType(int position) {
        int count = 0;
        for (ExpenseGroup group : expenseGroups) {
            if (position == count) return TYPE_HEADER;
            count++;
            if (position < count + group.getExpenses().size()) return TYPE_ITEM;
            count += group.getExpenses().size();
        }
        return TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (ExpenseGroup group : expenseGroups) {
            count += 1; // header
            count += group.getExpenses().size(); // items
        }
        return count;
    }

    // ---------------- CREATE VIEW ----------------
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(
                    R.layout.item_history_header, parent, false
            );
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(
                    R.layout.item_history_expense, parent, false
            );
            return new ExpenseViewHolder(view);
        }
    }

    // ---------------- BIND VIEW ----------------
    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        int count = 0;

        for (ExpenseGroup group : expenseGroups) {

            // HEADER
            if (position == count) {
                ((HeaderViewHolder) holder)
                        .bind(group.getMonth(), group.getExpenses().size());
                return;
            }

            count++; // skip header

            // ITEM
            if (position < count + group.getExpenses().size()) {
                Expense expense =
                        group.getExpenses().get(position - count);
                ((ExpenseViewHolder) holder).bind(expense);
                return;
            }

            count += group.getExpenses().size();
        }
    }

    // ---------------- UPDATE LIST ----------------
    public void updateList(List<ExpenseGroup> newGroups) {
        this.expenseGroups = newGroups;
        notifyDataSetChanged();
    }

    // ---------------- HEADER ----------------
    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        TextView tvMonth, tvTotal;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMonth = itemView.findViewById(R.id.tvMonth);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }

        void bind(String month, int count) {
            tvMonth.setText(month);
            tvTotal.setText(count + " transactions");
        }
    }

    // ---------------- ITEM ----------------
    static class ExpenseViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvCategory, tvAmount, tvTime;

        ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvExpenseName);
            tvCategory = itemView.findViewById(R.id.tvExpenseCategory);
            tvAmount = itemView.findViewById(R.id.tvExpenseAmount);
            tvTime = itemView.findViewById(R.id.tvExpenseTime);
        }

        void bind(Expense expense) {

            // Defensive null safety (important for Firestore data)
            tvName.setText(
                    expense.getNote() != null ? expense.getNote() : ""
            );

            tvCategory.setText(
                    expense.getCategory() != null ? expense.getCategory() : ""
            );

            tvAmount.setText("₹" + expense.getAmount());

            // Convert Firestore timestamp → readable time
            Timestamp ts = expense.getTimestamp();
            if (ts != null) {
                Date date = ts.toDate();
                SimpleDateFormat sdf =
                        new SimpleDateFormat("hh:mm a", Locale.getDefault());
                tvTime.setText(sdf.format(date));
            } else {
                tvTime.setText("");
            }
        }
    }
}
