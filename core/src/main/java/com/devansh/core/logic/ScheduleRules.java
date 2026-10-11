package com.devansh.core.logic;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure-function utilities for schedule-related checks.
 *
 * Every method is static and deterministic — no calls to LocalDate.now().
 * The caller always passes {@code today} explicitly.
 */
public final class ScheduleRules {

    // Prevent instantiation — this is a static utility class
    private ScheduleRules() {}

    /**
     * Checks whether a habit is scheduled on the given date.
     *
     * Rules:
     * 1. Dates before the habit's createdDate are NEVER scheduled.
     * 2. DAILY → every day is scheduled.
     * 3. SPECIFIC_DAYS → only days whose DayOfWeek is in the set.
     * 4. TIMES_PER_WEEK → every day is "loggable" (any day within the week
     *    may contribute to the weekly count), so we return true.
     *
     * @param habit the habit to check
     * @param date  the date to evaluate
     * @return true if the habit is due / loggable on {@code date}
     */
    public static boolean isScheduled(Habit habit, LocalDate date) {
        // Days before the habit existed are never scheduled
        if (date.isBefore(habit.getCreatedDate())) {
            return false;
        }

        Schedule schedule = habit.getSchedule();
        return switch (schedule.getType()) {
            case DAILY -> true;
            case SPECIFIC_DAYS -> schedule.getDaysOfWeek().contains(date.getDayOfWeek());
            // For TIMES_PER_WEEK, every day is a valid logging opportunity
            case TIMES_PER_WEEK -> true;
        };
    }

    /**
     * Checks whether a log entry meets the habit's completion target.
     *
     * YES_NO   → value >= 1 means done.
     * COUNTABLE → value >= target.
     * TIMED    → value (minutes) >= target.
     *
     * Partial progress is NOT considered complete.
     *
     * @param habit the habit defining the target
     * @param log   the log entry to evaluate
     * @return true if the logged value meets or exceeds the target
     */
    public static boolean isComplete(Habit habit, HabitLog log) {
        return log.getValue() >= habit.getTarget();
    }

    /**
     * Converts a {@link LocalDateTime} to the logical {@link LocalDate} it
     * belongs to for habit-tracking purposes.
     *
     * The current implementation uses the calendar date of the timestamp
     * (i.e. midnight is the boundary). A log at 23:59 counts for that day;
     * a log at 00:01 counts for the next day.
     *
     * This helper exists so the boundary logic lives in one place and is
     * easy to test.
     *
     * @param dateTime the timestamp to convert
     * @return the logical date for habit tracking
     */
    public static LocalDate toLogicalDate(LocalDateTime dateTime) {
        return dateTime.toLocalDate();
    }
}
