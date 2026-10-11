package com.devansh.habittracker.ui.model;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.ScheduleType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable UI model representing a habit card on the Today screen.
 *
 * <p>All domain calculations (streaks, schedule matching, completions)
 * are computed beforehand by the ViewModel delegating to :core logic,
 * keeping the View layer strictly declarative with zero business logic.
 */
public class TodayHabitUiModel {

    @NonNull
    private final Habit habit;

    @Nullable
    private final HabitLog todayLog;

    private final int streak;
    private final boolean completed;
    private final double currentValue;
    private final double targetValue;

    // For TIMES_PER_WEEK schedules
    private final int weekCompletions;
    private final int weekTarget;

    public TodayHabitUiModel(@NonNull Habit habit,
                            @Nullable HabitLog todayLog,
                            int streak,
                            boolean completed,
                            double currentValue,
                            double targetValue,
                            int weekCompletions,
                            int weekTarget) {
        this.habit = habit;
        this.todayLog = todayLog;
        this.streak = streak;
        this.completed = completed;
        this.currentValue = currentValue;
        this.targetValue = targetValue;
        this.weekCompletions = weekCompletions;
        this.weekTarget = weekTarget;
    }

    @NonNull
    public Habit getHabit() {
        return habit;
    }

    @Nullable
    public HabitLog getTodayLog() {
        return todayLog;
    }

    public int getStreak() {
        return streak;
    }

    public boolean isCompleted() {
        return completed;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public int getProgressPercentage() {
        if (targetValue <= 0) return 0;
        int pct = (int) Math.round((currentValue / targetValue) * 100);
        return Math.min(100, Math.max(0, pct));
    }

    public boolean isTimesPerWeek() {
        return habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK;
    }

    public int getWeekCompletions() {
        return weekCompletions;
    }

    public int getWeekTarget() {
        return weekTarget;
    }

    /**
     * Formats the progress text (e.g., "15 / 30 min", "20 / 20 pages", or "2/3 this week").
     */
    public String getProgressSubtitle() {
        if (isTimesPerWeek()) {
            return weekCompletions + "/" + weekTarget + " this week";
        }
        String unit = habit.getUnit() != null ? " " + habit.getUnit() : "";
        if (currentValue == (long) currentValue && targetValue == (long) targetValue) {
            return String.format("%d / %d%s", (long) currentValue, (long) targetValue, unit);
        }
        return String.format("%.1f / %.1f%s", currentValue, targetValue, unit);
    }
}
