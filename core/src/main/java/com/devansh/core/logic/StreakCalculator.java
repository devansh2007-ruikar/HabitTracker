package com.devansh.core.logic;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function calculator for current and longest habit streaks.
 *
 * All methods are static, deterministic, and never call LocalDate.now().
 * The caller always passes {@code today} explicitly.
 *
 * <h3>Streak rules</h3>
 * <ul>
 *   <li><b>DAILY / SPECIFIC_DAYS:</b> Walk backwards over scheduled days only.
 *       Unscheduled days (including days before createdDate) are silently
 *       skipped — they never break a streak. If today is scheduled but not
 *       yet complete, start counting from the previous scheduled day
 *       (today is a "grace" day). Stop at the first incomplete scheduled day.</li>
 *   <li><b>TIMES_PER_WEEK:</b> Count consecutive ISO weeks (Mon–Sun) where
 *       the user logged ≥ N completions. The current week is a "grace" week:
 *       if not yet met, it doesn't break the streak — counting starts from
 *       the previous week. Returns number of <em>weeks</em>.</li>
 * </ul>
 */
public final class StreakCalculator {

    // Prevent instantiation
    private StreakCalculator() {}

    /**
     * Calculates the current streak for a habit as of {@code today}.
     *
     * @param habit the habit to evaluate
     * @param logs  all log entries for this habit (may contain duplicates;
     *              if so, the last entry in the list for a given date wins)
     * @param today the current date (injected, never derived internally)
     * @return the current streak length (days for DAILY/SPECIFIC_DAYS,
     *         weeks for TIMES_PER_WEEK), or 0 if no streak
     */
    public static int currentStreak(Habit habit, List<HabitLog> logs, LocalDate today) {
        if (habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK) {
            return currentStreakWeekly(habit, logs, today);
        }
        return currentStreakDaily(habit, logs, today);
    }

    /**
     * Calculates the longest streak ever achieved for a habit.
     *
     * @param habit the habit to evaluate
     * @param logs  all log entries for this habit
     * @param today the current date
     * @return the longest streak length (days or weeks), or 0
     */
    public static int longestStreak(Habit habit, List<HabitLog> logs, LocalDate today) {
        if (habit.getSchedule().getType() == ScheduleType.TIMES_PER_WEEK) {
            return longestStreakWeekly(habit, logs, today);
        }
        return longestStreakDaily(habit, logs, today);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DAILY / SPECIFIC_DAYS streak helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Current streak for DAILY or SPECIFIC_DAYS habits.
     *
     * Algorithm:
     * 1. Build a date → completed map from logs (last-write-wins).
     * 2. Start from today. If today is scheduled and complete, count it.
     *    If today is scheduled but NOT complete, skip it (grace day).
     *    If today is not scheduled, skip it.
     * 3. Walk backwards day by day. For each day:
     *    - If it's before createdDate, stop.
     *    - If it's not scheduled, skip (continue walking).
     *    - If it's scheduled and complete, increment streak.
     *    - If it's scheduled and NOT complete, stop (streak broken).
     */
    private static int currentStreakDaily(Habit habit, List<HabitLog> logs, LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int streak = 0;

        // Determine start date: today if complete, else previous day
        LocalDate cursor = today;
        if (ScheduleRules.isScheduled(habit, today)) {
            if (isDayComplete(habit, logMap, today)) {
                streak = 1;  // Count today
            }
            // Grace: if today is scheduled but not complete, just start from yesterday
            cursor = today.minusDays(1);
        } else {
            // Today is not scheduled (e.g. Saturday for a weekday habit), start from yesterday
            cursor = today.minusDays(1);
        }

        // Walk backwards
        while (!cursor.isBefore(habit.getCreatedDate())) {
            if (!ScheduleRules.isScheduled(habit, cursor)) {
                // Not scheduled → skip this day, doesn't break streak
                cursor = cursor.minusDays(1);
                continue;
            }
            // Scheduled day: must be complete to continue streak
            if (isDayComplete(habit, logMap, cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;  // Streak broken
            }
        }

        return streak;
    }

    /**
     * Longest streak for DAILY or SPECIFIC_DAYS habits.
     *
     * Walks forward from createdDate to today, tracking scheduled days.
     */
    private static int longestStreakDaily(Habit habit, List<HabitLog> logs, LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int longest = 0;
        int current = 0;

        // Walk forward from createdDate through today
        LocalDate cursor = habit.getCreatedDate();
        while (!cursor.isAfter(today)) {
            if (!ScheduleRules.isScheduled(habit, cursor)) {
                // Not scheduled → skip, streak survives
                cursor = cursor.plusDays(1);
                continue;
            }

            // Scheduled day
            if (isDayComplete(habit, logMap, cursor)) {
                current++;
                longest = Math.max(longest, current);
            } else {
                // If this is today and not yet complete, the streak is still alive
                // but we don't count today
                if (cursor.equals(today)) {
                    // Don't break — today is a grace day for current streak
                    // But for longest, only count what we already have
                    longest = Math.max(longest, current);
                } else {
                    // Past incomplete day → streak resets
                    current = 0;
                }
            }
            cursor = cursor.plusDays(1);
        }

        return longest;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TIMES_PER_WEEK streak helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Current streak for TIMES_PER_WEEK habits (returns weeks).
     *
     * Algorithm:
     * 1. Build a weekYear → completionCount map.
     * 2. Determine the current ISO week.
     * 3. If the current week is already met, count it and move to previous.
     *    If not met, skip it (grace week) and start from previous.
     * 4. Walk backwards week by week until a week is not met or we pass
     *    the creation week.
     */
    private static int currentStreakWeekly(Habit habit, List<HabitLog> logs, LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int target = habit.getSchedule().getTimesPerWeek();
        int streak = 0;

        // Start from the Monday of the current week
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        // Check current week
        int currentWeekCount = countCompletionsInWeek(habit, logMap, weekStart, today);
        if (currentWeekCount >= target) {
            streak = 1;
        }
        // Move to previous week regardless (grace if not met)
        weekStart = weekStart.minusWeeks(1);

        // Walk backwards through previous weeks
        while (!weekStart.isBefore(habit.getCreatedDate().with(DayOfWeek.MONDAY))) {
            // For past weeks, count all 7 days (or from createdDate if mid-week)
            LocalDate weekEnd = weekStart.plusDays(6); // Sunday
            int weekCount = countCompletionsInWeek(habit, logMap, weekStart, weekEnd);
            if (weekCount >= target) {
                streak++;
                weekStart = weekStart.minusWeeks(1);
            } else {
                break;  // Streak broken
            }
        }

        return streak;
    }

    /**
     * Longest streak for TIMES_PER_WEEK habits (returns weeks).
     */
    private static int longestStreakWeekly(Habit habit, List<HabitLog> logs, LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int target = habit.getSchedule().getTimesPerWeek();
        int longest = 0;
        int current = 0;

        // Start from the Monday of the creation week
        LocalDate weekStart = habit.getCreatedDate().with(DayOfWeek.MONDAY);
        LocalDate currentWeekMonday = today.with(DayOfWeek.MONDAY);

        while (!weekStart.isAfter(currentWeekMonday)) {
            // Determine the last day to check in this week
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekStart.equals(currentWeekMonday)) {
                // Current week: only count up to today
                weekEnd = today;
            }

            int weekCount = countCompletionsInWeek(habit, logMap, weekStart, weekEnd);

            if (weekCount >= target) {
                current++;
                longest = Math.max(longest, current);
            } else {
                // If current week and not yet met, it's a grace week
                if (weekStart.equals(currentWeekMonday)) {
                    longest = Math.max(longest, current);
                } else {
                    current = 0;
                }
            }
            weekStart = weekStart.plusWeeks(1);
        }

        return longest;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Shared helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds a date → value map from a list of logs.
     * If there are duplicate logs for the same date, the LAST one in the list
     * wins (simulating "latest edit wins").
     */
    private static Map<LocalDate, Double> buildLogMap(List<HabitLog> logs) {
        Map<LocalDate, Double> map = new HashMap<>();
        for (HabitLog log : logs) {
            // Overwrite on collision → last entry wins
            map.put(log.getDate(), log.getValue());
        }
        return map;
    }

    /**
     * Checks whether a specific day is complete for the given habit.
     *
     * @param habit  the habit (provides the target)
     * @param logMap date → value map
     * @param date   the date to check
     * @return true if the logged value on that date meets the target
     */
    private static boolean isDayComplete(Habit habit, Map<LocalDate, Double> logMap, LocalDate date) {
        Double value = logMap.get(date);
        return value != null && value >= habit.getTarget();
    }

    /**
     * Counts how many days within [weekStart, weekEnd] the habit was completed.
     * Only counts days on or after the habit's createdDate.
     */
    private static int countCompletionsInWeek(Habit habit, Map<LocalDate, Double> logMap,
                                              LocalDate weekStart, LocalDate weekEnd) {
        int count = 0;
        LocalDate day = weekStart;
        while (!day.isAfter(weekEnd)) {
            // Don't count days before the habit was created
            if (!day.isBefore(habit.getCreatedDate()) && isDayComplete(habit, logMap, day)) {
                count++;
            }
            day = day.plusDays(1);
        }
        return count;
    }
}
