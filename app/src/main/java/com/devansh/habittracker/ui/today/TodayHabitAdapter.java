package com.devansh.habittracker.ui.today;

import android.content.res.ColorStateList;
import android.view.HapticFeedbackConstants;
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

/**
 * Adapter presenting scheduled habit cards with color accent bar, circular progress ring,
 * JetBrains Mono streak numbers, and quick-logging controls with tactile check animations.
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

    private static final int[] ACCENT_COLORS = {
            R.color.gdg_blue,
            R.color.gdg_green,
            R.color.gdg_yellow,
            R.color.gdg_red,
            R.color.gdg_purple
    };

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
            binding.tvProgressSubtitle.setText(item.getProgressSubtitle());

            // Color Accent Bar based on habit ID (GDG Palette)
            int accentColorRes = ACCENT_COLORS[(int) (Math.abs(item.getHabit().getId()) % ACCENT_COLORS.length)];
            int accentColor = ContextCompat.getColor(itemView.getContext(), accentColorRes);
            binding.viewAccentBar.setBackgroundTintList(ColorStateList.valueOf(accentColor));

            // Circular Progress Ring & Completion Icon
            int pct = Math.min(100, Math.max(0, item.getProgressPercentage()));
            binding.progressRing.setProgress(pct);

            int colorComplete = ContextCompat.getColor(itemView.getContext(), R.color.status_complete);

            if (item.isCompleted()) {
                binding.progressRing.setIndicatorColor(colorComplete);
                binding.ivProgressCompleteIcon.setVisibility(View.VISIBLE);
                binding.tvProgressPercent.setVisibility(View.GONE);
            } else {
                binding.progressRing.setIndicatorColor(accentColor);
                binding.ivProgressCompleteIcon.setVisibility(View.GONE);
                binding.tvProgressPercent.setVisibility(View.VISIBLE);
                binding.tvProgressPercent.setText(pct + "%");
            }

            // Quick logging visibility by HabitType
            HabitType type = item.getHabit().getType();
            binding.layoutActionYesNo.setVisibility(type == HabitType.YES_NO ? View.VISIBLE : View.GONE);
            binding.layoutActionCountable.setVisibility(type == HabitType.COUNTABLE ? View.VISIBLE : View.GONE);
            binding.layoutActionTimed.setVisibility(type == HabitType.TIMED ? View.VISIBLE : View.GONE);

            // 1. YES_NO configuration
            if (type == HabitType.YES_NO) {
                if (item.isCompleted()) {
                    binding.btnToggleYesNo.setText(R.string.quick_log_completed_check);
                    binding.btnToggleYesNo.setIconResource(R.drawable.ic_check);
                    binding.btnToggleYesNo.setIconTint(ColorStateList.valueOf(colorComplete));
                } else {
                    binding.btnToggleYesNo.setText(R.string.quick_log_complete);
                    binding.btnToggleYesNo.setIconResource(R.drawable.ic_check);
                    binding.btnToggleYesNo.setIconTint(ColorStateList.valueOf(accentColor));
                }
                binding.btnToggleYesNo.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    animateCheck(binding.layoutProgressRing);
                    animateCheck(binding.btnToggleYesNo);
                    listener.onToggleYesNo(item);
                });
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

                binding.btnDecrementCount.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    listener.onAdjustCountable(item, -1.0);
                });
                binding.btnIncrementCount.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (item.getCurrentValue() + 1.0 >= item.getTargetValue()) {
                        animateCheck(binding.layoutProgressRing);
                    }
                    listener.onAdjustCountable(item, 1.0);
                });
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

                binding.btnTimerToggle.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    listener.onTimerToggleClick(item);
                });
                binding.btnTimerManual.setOnClickListener(v -> listener.onTimerManualClick(item));
            }

            // Entire card click -> opens habit detail/stats
            binding.cardHabit.setOnClickListener(v -> listener.onHabitClick(item));
        }

        private static void animateCheck(@NonNull View view) {
            view.setScaleX(0.88f);
            view.setScaleY(0.88f);
            view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(130)
                    .withEndAction(() -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
        }
    }
}
