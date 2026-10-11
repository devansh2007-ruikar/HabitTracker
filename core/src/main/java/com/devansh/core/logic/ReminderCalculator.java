package com.devansh.core.logic;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.Schedule;
import com.devansh.core.model.ScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function calculator for habit reminder trigger times.
 *
 * <p>All methods are deterministic, static, and completely free of
 * Android dependencies. The caller always injects {@code now}.
 *
 * <h3>Scheduling rules</h3>
 * <ul>
 *   <li><b>DAILY:</b> Triggers every day at {@code reminderTime}. If today's reminder
 *       has already passed, schedules for tomorrow.</li>
 *   <li><b>SPECIFIC_DAYS:</b> Triggers only on scheduled days of the week.
 *       Walks forward to the next scheduled day at {@code reminderTime}.</li>
 *   <li><b>TIMES_PER_WEEK:</b> Triggers daily at {@code reminderTime} until the
 *       weekly completion target is achieved. Once the weekly target is met,
 *       skips the remainder of the ISO week and schedules for the following Monday.</li>
 *   <li>Never schedules prior to the habit's {@code createdDate}.</li>
 *   <li>Returns {@code null} if the habit is archived or has no reminder configured.</li>
 * </ul>
 */
public final class ReminderCalculator {

    private ReminderCalculator() {
        // Prevent instantiation
    }

    /**
     * Computes the next {@link LocalDateTime} when an alarm should fire for this habit.
     *
     * @param habit the habit to evaluate (must have non-null reminderTime to schedule)
     * @param logs  historical log entries (used for TIMES_PER_WEEK target checking)
     * @param now   the current date and time (injected, never derived internally)
     * @return the next trigger timestamp, or {@code null} if no reminder should be scheduled
     */
    public static LocalDateTime computeNextTrigger(Habit habit, List<HabitLog> logs, LocalDateTime now) {
        if (habit == null || habit.getReminderTime() == null || habit.isArchived()) {
            return null;
        }

        LocalDate today = now.toLocalDate();
        LocalTime reminderTime = habit.getReminderTime();
        Schedule schedule = habit.getSchedule();

        // Check whether today at reminderTime is still in the future
        LocalDateTime todayAtReminder = LocalDateTime.of(today, reminderTime);
        boolean canTriggerToday = todayAtReminder.isAfter(now);

        LocalDate startDate = canTriggerToday ? today : today.plusDays(1);

        if (schedule.getType() == ScheduleType.TIMES_PER_WEEK) {
            return computeNextTriggerWeekly(habit, logs, now, startDate);
        } else {
            return computeNextTriggerDailyOrSpecific(habit, now, startDate);
        }
    }

    /**
     * Triggers for DAILY and SPECIFIC_DAYS schedules.
     */
    private static LocalDateTime computeNextTriggerDailyOrSpecific(Habit habit,
                                                                   LocalDateTime now,
                                                                   LocalDate startDate) {
        LocalDate cursor = startDate;
        // Search up to 366 days ahead to prevent any infinite loops
        LocalDate cutoff = now.toLocalDate().plusYears(1);

        while (!cursor.isAfter(cutoff)) {
            // Must be on or after createdDate and scheduled by rules
            if (!cursor.isBefore(habit.getCreatedDate()) && ScheduleRules.isScheduled(habit, cursor)) {
                LocalDateTime candidate = LocalDateTime.of(cursor, habit.getReminderTime());
                if (candidate.isAfter(now)) {
                    return candidate;
                }
            }
            cursor = cursor.plusDays(1);
        }

        return null;
    }

    /**
     * Triggers for TIMES_PER_WEEK schedules:
     * Daily reminders until the weekly target is met, then pauses until next Monday.
     */
    private static LocalDateTime computeNextTriggerWeekly(Habit habit,
                                                          List<HabitLog> logs,
                                                          LocalDateTime now,
                                                          LocalDate startDate) {
        Map<LocalDate, Double> logMap = buildLogMap(logs);
        int targetTimesPerWeek = habit.getSchedule().getTimesPerWeek();

        LocalDate cursor = startDate;
        if (cursor.isBefore(habit.getCreatedDate())) {
            cursor = habit.getCreatedDate();
        }

        LocalDate cutoff = now.toLocalDate().plusYears(1);

        while (!cursor.isAfter(cutoff)) {
            LocalDate weekMonday = cursor.with(DayOfWeek.MONDAY);
            LocalDate weekSunday = cursor.with(DayOfWeek.SUNDAY);

            // Count completions achieved in the ISO week of cursor
            int completionsInWeek = countCompletionsInWeek(habit, logMap, weekMonday, weekSunday);

            if (completionsInWeek >= targetTimesPerWeek) {
                // Weekly goal already met! Skip remainder of week and jump to next Monday
                cursor = weekSunday.plusDays(1);
                continue;
            }

            // Weekly goal not yet met -> cursor date is eligible
            LocalDateTime candidate = LocalDateTime.of(cursor, habit.getReminderTime());
            if (candidate.isAfter(now) && !cursor.isBefore(habit.getCreatedDate())) {
                return candidate;
            }

            cursor = cursor.plusDays(1);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helper Methods
    // ═══════════════════════════════════════════════════════════════════

    private static Map<LocalDate, Double> buildLogMap(List<HabitLog> logs) {
        Map<LocalDate, Double> map = new HashMap<>();
        if (logs != null) {
            for (HabitLog log : logs) {
                map.put(log.getDate(), log.getValue());
            }
        }
        return map;
    }

    private static int countCompletionsInWeek(Habit habit, Map<LocalDate, Double> logMap,
                                              LocalDate weekStart, LocalDate weekEnd) {
        int count = 0;
        LocalDate day = weekStart;
        while (!day.isAfter(weekEnd)) {
            if (!day.isBefore(habit.getCreatedDate())) {
                Double val = logMap.get(day);
                if (val != null && val >= habit.getTarget()) {
                    count++;
                }
            }
            day = day.plusDays(1);
        }
        return count;
    }
}
