package com.devansh.habittracker.ui.today;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.devansh.core.model.HabitType;
import com.devansh.habittracker.R;
import com.devansh.habittracker.databinding.ItemTodayHabitBinding;
import com.devansh.habittracker.ui.model.TodayHabitUiModel;

import java.util.Objects;

/**
 * Adapter presenting scheduled habit cards with quick-logging controls.
 */
public class TodayHabitAdapter extends ListAdapter<TodayHabitUiModel, TodayHabitAdapter.HabitViewHolder> {

    public interface OnHabitActionListener {
        void onHabitClick(@NonNull TodayHabitUiModel item);
        void onToggleYesNo(@NonNull TodayHabitUiModel item);
        void onAdjustCountable(@NonNull TodayHabitUiModel item, double delta);
        void onExactValueClick(@NonNull TodayHabitUiModel item);
        void onTimerToggleClick(@NonNull TodayHabitUiModel item);
        void onTimerManualClick(@NonNull TodayHabitUiModel item);
    }

    private final OnHabitActionListener listener;
    private long activeTimerHabitId = -1;
    private boolean isTimerRunning = false;
    private int timerSeconds = 0;

    public TodayHabitAdapter(@NonNull OnHabitActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void updateTimerState(long activeHabitId, boolean isRunning, int seconds) {
        this.activeTimerHabitId = activeHabitId;
        this.isTimerRunning = isRunning;
        this.timerSeconds = seconds;
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<TodayHabitUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TodayHabitUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull TodayHabitUiModel oldItem, @NonNull TodayHabitUiModel newItem) {
                    return oldItem.getHabit().getId() == newItem.getHabit().getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull TodayHabitUiModel oldItem, @NonNull TodayHabitUiModel newItem) {
                    return oldItem.getStreak() == newItem.getStreak()
                            && oldItem.isCompleted() == newItem.isCompleted()
                            && Double.compare(oldItem.getCurrentValue(), newItem.getCurrentValue()) == 0
                            && oldItem.getWeekCompletions() == newItem.getWeekCompletions();
                }
            };

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTodayHabitBinding binding = ItemTodayHabitBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new HabitViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        TodayHabitUiModel item = getItem(position);
        holder.bind(item, listener, activeTimerHabitId, isTimerRunning, timerSeconds);
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        final ItemTodayHabitBinding binding;

        HabitViewHolder(@NonNull ItemTodayHabitBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull TodayHabitUiModel item,
                  @NonNull OnHabitActionListener listener,
                  long activeTimerId,
                  boolean isTimerRunning,
                  int timerSeconds) {

            binding.tvHabitName.setText(item.getHabit().getName());
            binding.tvStreakCount.setText(String.valueOf(item.getStreak()));

            // Subtitle & Progress
            binding.tvProgressSubtitle.setText(item.getProgressSubtitle());
            int pct = item.getProgressPercentage();
            binding.tvProgressPercent.setText(pct + "%");
            binding.progressBar.setProgress(pct);

            int colorComplete = ContextCompat.getColor(itemView.getContext(), R.color.status_complete);
            int colorPrimary = ContextCompat.getColor(itemView.getContext(), R.color.primary);

            if (item.isCompleted()) {
                binding.progressBar.setIndicatorColor(colorComplete);
                binding.tvProgressPercent.setTextColor(colorComplete);
            } else {
                binding.progressBar.setIndicatorColor(colorPrimary);
                binding.tvProgressPercent.setTextColor(colorPrimary);
            }

            // Quick logging visibility by HabitType
            HabitType type = item.getHabit().getType();
            binding.layoutActionYesNo.setVisibility(type == HabitType.YES_NO ? View.VISIBLE : View.GONE);
            binding.layoutActionCountable.setVisibility(type == HabitType.COUNTABLE ? View.VISIBLE : View.GONE);
            binding.layoutActionTimed.setVisibility(type == HabitType.TIMED ? View.VISIBLE : View.GONE);

            // 1. YES_NO configuration
            if (type == HabitType.YES_NO) {
                if (item.isCompleted()) {
                    binding.btnToggleYesNo.setText("Completed ✓");
                    binding.btnToggleYesNo.setIconResource(R.drawable.ic_check);
                } else {
                    binding.btnToggleYesNo.setText("Mark Complete");
                    binding.btnToggleYesNo.setIconResource(R.drawable.ic_check);
                }
                binding.btnToggleYesNo.setOnClickListener(v -> listener.onToggleYesNo(item));
            }

            // 2. COUNTABLE configuration
            if (type == HabitType.COUNTABLE) {
                String unit = item.getHabit().getUnit() != null ? " " + item.getHabit().getUnit() : "";
                String valText;
                if (item.getCurrentValue() == (long) item.getCurrentValue()) {
                    valText = (long) item.getCurrentValue() + unit;
                } else {
                    valText = String.format("%.1f%s", item.getCurrentValue(), unit);
                }
                binding.btnExactValue.setText(valText);

                binding.btnDecrementCount.setOnClickListener(v -> listener.onAdjustCountable(item, -1.0));
                binding.btnIncrementCount.setOnClickListener(v -> listener.onAdjustCountable(item, 1.0));
                binding.btnExactValue.setOnClickListener(v -> listener.onExactValueClick(item));
            }

            // 3. TIMED configuration
            if (type == HabitType.TIMED) {
                boolean isCurrentHabitTimer = (activeTimerId == item.getHabit().getId());
                if (isCurrentHabitTimer && isTimerRunning) {
                    int mins = timerSeconds / 60;
                    int secs = timerSeconds % 60;
                    binding.btnTimerToggle.setText(String.format("⏸ Pause (%02d:%02d)", mins, secs));
                    binding.btnTimerToggle.setIconResource(R.drawable.ic_pause);
                } else if (isCurrentHabitTimer && timerSeconds > 0) {
                    int mins = timerSeconds / 60;
                    int secs = timerSeconds % 60;
                    binding.btnTimerToggle.setText(String.format("▶ Resume (%02d:%02d)", mins, secs));
                    binding.btnTimerToggle.setIconResource(R.drawable.ic_play);
                } else {
                    binding.btnTimerToggle.setText("▶ Start Timer");
                    binding.btnTimerToggle.setIconResource(R.drawable.ic_play);
                }

                binding.btnTimerToggle.setOnClickListener(v -> listener.onTimerToggleClick(item));
                binding.btnTimerManual.setOnClickListener(v -> listener.onTimerManualClick(item));
            }

            // Entire card click -> opens habit detail/stats
            itemView.setOnClickListener(v -> listener.onHabitClick(item));
        }
    }
}
