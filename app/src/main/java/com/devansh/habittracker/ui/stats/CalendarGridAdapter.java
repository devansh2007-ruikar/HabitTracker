package com.devansh.habittracker.ui.stats;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.core.model.DayStatus;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ItemCalendarDayBinding;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Adapter rendering the 7-column calendar month view color-coded by {@link DayStatus}.
 */
public class CalendarGridAdapter extends RecyclerView.Adapter<CalendarGridAdapter.DayViewHolder> {

    public interface OnCalendarDayClickListener {
        void onDayClicked(@NonNull LocalDate date);
    }

    private YearMonth currentMonth = YearMonth.now();
    private Map<LocalDate, DayStatus> statusMap = Collections.emptyMap();
    private final OnCalendarDayClickListener listener;

    private final List<LocalDate> gridDates = new ArrayList<>();

    public CalendarGridAdapter(@NonNull OnCalendarDayClickListener listener) {
        this.listener = listener;
        rebuildGrid();
    }

    public void updateData(@NonNull YearMonth month, @NonNull Map<LocalDate, DayStatus> statusMap) {
        this.currentMonth = month;
        this.statusMap = statusMap;
        rebuildGrid();
        notifyDataSetChanged();
    }

    private void rebuildGrid() {
        gridDates.clear();
        LocalDate firstOfMonth = currentMonth.atDay(1);
        int daysInMonth = currentMonth.lengthOfMonth();

        // Monday = 1, Sunday = 7. Prefix with null for padding before 1st of month
        int leadOffset = firstOfMonth.getDayOfWeek().getValue() - 1; // 0 for Monday, 6 for Sunday
        for (int i = 0; i < leadOffset; i++) {
            gridDates.add(null);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            gridDates.add(currentMonth.atDay(day));
        }
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarDayBinding binding = ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        LocalDate date = gridDates.get(position);
        if (date == null) {
            holder.binding.tvDayCell.setVisibility(View.INVISIBLE);
            holder.itemView.setOnClickListener(null);
            return;
        }

        holder.binding.tvDayCell.setVisibility(View.VISIBLE);
        holder.binding.tvDayCell.setText(String.valueOf(date.getDayOfMonth()));

        DayStatus status = statusMap.getOrDefault(date, DayStatus.FUTURE);
        bindStatusStyling(holder, date, status);

        // Clickable for past & today dates
        if (!date.isAfter(LocalDate.now())) {
            holder.itemView.setOnClickListener(v -> listener.onDayClicked(date));
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    private void bindStatusStyling(DayViewHolder holder, LocalDate date, DayStatus status) {
        int colorComplete = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_complete);
        int colorPartial = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_partial);
        int colorMissed = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_missed);
        int colorNotScheduled = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_not_scheduled_container);
        int textNotScheduled = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_not_scheduled);
        int textNormal = ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface);
        int textWhite = ContextCompat.getColor(holder.itemView.getContext(), R.color.white);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);

        switch (status) {
            case COMPLETE -> {
                shape.setColor(colorComplete);
                holder.binding.tvDayCell.setBackground(shape);
                holder.binding.tvDayCell.setTextColor(textWhite);
            }
            case PARTIAL -> {
                shape.setColor(colorPartial);
                holder.binding.tvDayCell.setBackground(shape);
                holder.binding.tvDayCell.setTextColor(textWhite);
            }
            case MISSED -> {
                shape.setColor(colorMissed);
                holder.binding.tvDayCell.setBackground(shape);
                holder.binding.tvDayCell.setTextColor(textWhite);
            }
            case NOT_SCHEDULED -> {
                shape.setColor(colorNotScheduled);
                holder.binding.tvDayCell.setBackground(shape);
                holder.binding.tvDayCell.setTextColor(textNotScheduled);
            }
            case FUTURE -> {
                holder.binding.tvDayCell.setBackground(null);
                holder.binding.tvDayCell.setTextColor(textNotScheduled);
            }
        }
    }

    @Override
    public int getItemCount() {
        return gridDates.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final ItemCalendarDayBinding binding;

        DayViewHolder(@NonNull ItemCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
