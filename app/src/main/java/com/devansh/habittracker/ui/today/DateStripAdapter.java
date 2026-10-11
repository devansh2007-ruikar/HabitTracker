package com.devansh.habittracker.ui.today;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ItemDateStripBinding;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter displaying the horizontal 7-day date strip with today highlight and all-done dot indicator.
 */
public class DateStripAdapter extends RecyclerView.Adapter<DateStripAdapter.DateViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(@NonNull LocalDate date);
    }

    private final List<LocalDate> dates = new ArrayList<>();
    private LocalDate selectedDate = LocalDate.now();
    private Map<LocalDate, Boolean> allDoneDates = Collections.emptyMap();
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

    public void setAllDoneDates(@NonNull Map<LocalDate, Boolean> allDoneDates) {
        this.allDoneDates = allDoneDates;
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
        boolean isAllDone = Boolean.TRUE.equals(allDoneDates.get(date));

        String dayOfWeekStr = isToday ? holder.itemView.getContext().getString(R.string.today_highlight_label) :
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase(Locale.getDefault());
        holder.binding.tvDayOfWeek.setText(dayOfWeekStr);
        holder.binding.tvDayNumber.setText(String.valueOf(date.getDayOfMonth()));

        // Green dot indicator when all scheduled habits on this date are complete
        holder.binding.viewDotIndicator.setVisibility(isAllDone ? View.VISIBLE : View.GONE);

        int contextColorSurface = ContextCompat.getColor(holder.itemView.getContext(), R.color.surface);
        int contextColorPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.primary);
        int contextColorOnPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_primary);
        int contextColorPrimaryContainer = ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_container);
        int contextColorOnPrimaryContainer = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_primary_container);
        int contextColorOnSurface = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface);
        int contextColorOnSurfaceVariant = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface_variant);
        int contextColorOutline = ContextCompat.getColor(holder.itemView.getContext(), R.color.outline);

        if (isSelected) {
            // Selected day: solid primary accent fill with neo-brutalist outline
            holder.binding.cardDateStrip.setCardBackgroundColor(contextColorPrimary);
            holder.binding.cardDateStrip.setStrokeColor(contextColorOutline);
            holder.binding.tvDayOfWeek.setTextColor(contextColorOnPrimary);
            holder.binding.tvDayNumber.setTextColor(contextColorOnPrimary);
        } else if (isToday) {
            // Today (not currently selected): highlighted container & primary outline
            holder.binding.cardDateStrip.setCardBackgroundColor(contextColorPrimaryContainer);
            holder.binding.cardDateStrip.setStrokeColor(contextColorPrimary);
            holder.binding.tvDayOfWeek.setTextColor(contextColorOnPrimaryContainer);
            holder.binding.tvDayNumber.setTextColor(contextColorOnPrimaryContainer);
        } else {
            // Other past days
            holder.binding.cardDateStrip.setCardBackgroundColor(contextColorSurface);
            holder.binding.cardDateStrip.setStrokeColor(contextColorOutline);
            holder.binding.tvDayOfWeek.setTextColor(contextColorOnSurfaceVariant);
            holder.binding.tvDayNumber.setTextColor(contextColorOnSurface);
        }

        String cd = (isToday ? "Today, " : "") + dayOfWeekStr + " " + date.getDayOfMonth() + (isAllDone ? ", all habits complete" : "");
        holder.binding.cardDateStrip.setContentDescription(cd);

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
