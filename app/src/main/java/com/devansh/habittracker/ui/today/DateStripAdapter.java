package com.devansh.habittracker.ui.today;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ItemDateStripBinding;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter displaying the horizontal date strip for past-day view and correction.
 */
public class DateStripAdapter extends RecyclerView.Adapter<DateStripAdapter.DateViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(@NonNull LocalDate date);
    }

    private final List<LocalDate> dates = new ArrayList<>();
    private LocalDate selectedDate = LocalDate.now();
    private final OnDateSelectedListener listener;

    public DateStripAdapter(@NonNull OnDateSelectedListener listener) {
        this.listener = listener;
        generateLast7Days();
    }

    private void generateLast7Days() {
        dates.clear();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            dates.add(today.minusDays(i));
        }
    }

    public void setSelectedDate(@NonNull LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDateStripBinding binding = ItemDateStripBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new DateViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        LocalDate date = dates.get(position);
        boolean isSelected = date.equals(selectedDate);
        boolean isToday = date.equals(LocalDate.now());

        String dayOfWeekStr = isToday ? "Today" :
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        holder.binding.tvDayOfWeek.setText(dayOfWeekStr);
        holder.binding.tvDayNumber.setText(String.valueOf(date.getDayOfMonth()));

        int contextColorSurface = ContextCompat.getColor(holder.itemView.getContext(), R.color.surface);
        int contextColorPrimaryContainer = ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_container);
        int contextColorOnPrimaryContainer = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_primary_container);
        int contextColorOnSurface = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface);
        int contextColorOnSurfaceVariant = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface_variant);
        int contextColorPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.primary);
        int contextColorOutline = ContextCompat.getColor(holder.itemView.getContext(), R.color.outline);

        if (isSelected) {
            holder.binding.cardDateStrip.setCardBackgroundColor(contextColorPrimaryContainer);
            holder.binding.cardDateStrip.setStrokeColor(contextColorPrimary);
            holder.binding.tvDayOfWeek.setTextColor(contextColorOnPrimaryContainer);
            holder.binding.tvDayNumber.setTextColor(contextColorOnPrimaryContainer);
        } else {
            holder.binding.cardDateStrip.setCardBackgroundColor(contextColorSurface);
            holder.binding.cardDateStrip.setStrokeColor(contextColorOutline);
            holder.binding.tvDayOfWeek.setTextColor(contextColorOnSurfaceVariant);
            holder.binding.tvDayNumber.setTextColor(contextColorOnSurface);
        }

        holder.itemView.setOnClickListener(v -> listener.onDateSelected(date));
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        final ItemDateStripBinding binding;

        DateViewHolder(@NonNull ItemDateStripBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
