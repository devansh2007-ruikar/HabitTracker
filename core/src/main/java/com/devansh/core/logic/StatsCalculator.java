package com.devansh.core.logic;

import com.devansh.core.model.DayStatus;
import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function calculator for habit statistics.
 *
 * All methods are static, deterministic, and never call LocalDate.now().
 * The caller always passes {@code today} explicitly.
 */
public final class StatsCalculator {

    // Prevent instantiation
    private StatsCalculator() {}

    /**
     * Calculates the completion rate over a date range.
     *
     * Rate = (completed scheduled days) / (total scheduled days).
     *
     * Rules:
     * - Days before createdDate are excluded.
     * - Future days (after today) are excluded.
     * - Today is only counted if the habit is complete on today.
     *   If today is scheduled but not complete, it is NOT counted in
     *   either numerator or denominator (grace day for stats too).
     * - TIMES_PER_WEEK: each day in the range that the user completed
     *   counts; the denominator is the number of days in range that are
     *   on or after createdDate and on or before today. This gives a
     *   daily-granularity rate even for weekly schedules.
     *
     * @param habit    the habit
     * @param logs     log entries for this habit
     * @param from     start of the date range (inclusive)
     * @param to       end of the date range (inclusive)
     * @param today    the current date
     * @return completion rate as a double in [0.0, 1.0], or 0.0 if no
     *         scheduled days exist in the range
     */
    public static double completionRate(Habit habit, List<HabitLog> logs,
                                        LocalDate from, LocalDate to, LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int scheduled = 0;
        int completed = 0;

        // Clamp the range: no earlier than createdDate, no later than today
        LocalDate start = from.isBefore(habit.getCreatedDate()) ? habit.getCreatedDate() : from;
        LocalDate end = to.isAfter(today) ? today : to;

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            if (ScheduleRules.isScheduled(habit, cursor)) {
                // Skip today if not complete (grace day)
                if (cursor.equals(today)) {
                    if (isDayComplete(habit, logMap, cursor)) {
                        scheduled++;
                        completed++;
                    }
                    // If not complete today, skip both numerator and denominator
                } else {
                    // Past scheduled day
                    scheduled++;
                    if (isDayComplete(habit, logMap, cursor)) {
                        completed++;
                    }
                }
            }
            cursor = cursor.plusDays(1);
        }

        return scheduled == 0 ? 0.0 : (double) completed / scheduled;
    }

    /**
     * Returns the total number of completed entries across all provided logs.
     *
     * @param habit the habit (provides the target threshold)
     * @param logs  log entries for this habit
     * @return count of logs where value >= target
     */
    public static int totalCompletions(Habit habit, List<HabitLog> logs) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int count = 0;
        for (Map.Entry<LocalDate, Double> entry : logMap.entrySet()) {
            if (entry.getValue() >= habit.getTarget()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a per-ISO-week completion count map for the given date range.
     *
     * The key is the Monday of each ISO week; the value is the number of
     * completed days in that week.
     *
     * @param habit the habit
     * @param logs  log entries
     * @param from  start of range (inclusive)
     * @param to    end of range (inclusive)
     * @param today the current date
     * @return ordered map of week-start (Monday) → completion count
     */
    public static Map<LocalDate, Integer> perWeekCompletions(Habit habit, List<HabitLog> logs,
                                                             LocalDate from, LocalDate to,
                                                             LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        Map<LocalDate, Integer> weekMap = new LinkedHashMap<>();

        // Clamp range
        LocalDate start = from.isBefore(habit.getCreatedDate()) ? habit.getCreatedDate() : from;
        LocalDate end = to.isAfter(today) ? today : to;

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate weekMonday = cursor.with(DayOfWeek.MONDAY);
            if (isDayComplete(habit, logMap, cursor)) {
                weekMap.merge(weekMonday, 1, Integer::sum);
            } else {
                // Ensure the week key exists even if count is 0
                weekMap.putIfAbsent(weekMonday, 0);
            }
            cursor = cursor.plusDays(1);
        }

        return weekMap;
    }

    /**
     * Builds a day-by-day status map for a calendar view.
     *
     * Each date in [from, to] is assigned a {@link DayStatus}:
     * - FUTURE: date is after today
     * - NOT_SCHEDULED: habit is not scheduled on this date (or before createdDate)
     * - COMPLETE: scheduled and value >= target
     * - PARTIAL: scheduled and 0 < value < target
     * - MISSED: scheduled and no log or value == 0
     *
     * @param habit the habit
     * @param logs  log entries
     * @param from  start of range (inclusive)
     * @param to    end of range (inclusive)
     * @param today the current date
     * @return ordered map of date → DayStatus
     */
    public static Map<LocalDate, DayStatus> buildDayStatusMap(Habit habit, List<HabitLog> logs,
                                                               LocalDate from, LocalDate to,
                                                               LocalDate today) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        Map<LocalDate, DayStatus> statusMap = new LinkedHashMap<>();

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            if (cursor.isAfter(today)) {
                statusMap.put(cursor, DayStatus.FUTURE);
            } else if (!ScheduleRules.isScheduled(habit, cursor)) {
                statusMap.put(cursor, DayStatus.NOT_SCHEDULED);
            } else {
                // Scheduled day on or before today
                Double value = logMap.get(cursor);
                if (value == null || value == 0.0) {
                    statusMap.put(cursor, DayStatus.MISSED);
                } else if (value >= habit.getTarget()) {
                    statusMap.put(cursor, DayStatus.COMPLETE);
                } else {
                    statusMap.put(cursor, DayStatus.PARTIAL);
                }
            }
            cursor = cursor.plusDays(1);
        }

        return statusMap;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Shared helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Builds a date → value map. Last entry for a date wins (duplicate handling). */
    private static Map<LocalDate, Double> buildLogMap(List<HabitLog> logs) {
        Map<LocalDate, Double> map = new HashMap<>();
        for (HabitLog log : logs) {
            map.put(log.getDate(), log.getValue());
        }
        return map;
    }

    /** Checks whether a day is complete for the given habit. */
    private static boolean isDayComplete(Habit habit, Map<LocalDate, Double> logMap, LocalDate date) {
        Double value = logMap.get(date);
        return value != null && value >= habit.getTarget();
    }
}
