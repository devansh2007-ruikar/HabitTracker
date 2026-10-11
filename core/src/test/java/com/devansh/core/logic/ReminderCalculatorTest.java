package com.devansh.core.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.devansh.core.model.Habit;
import com.devansh.core.model.HabitLog;
import com.devansh.core.model.HabitType;
import com.devansh.core.model.Schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Unit tests verifying pure-Java reminder calculation rules in :core.
 */
class ReminderCalculatorTest {

    @Nested
    @DisplayName("DAILY habits")
    class DailyHabits {

        @Test
        @DisplayName("Reminder time in future today -> triggers today at reminderTime")
        void testReminderTimeInFutureToday() {
            Habit habit = new Habit(
                    1L, "Water", HabitType.COUNTABLE, Schedule.daily(),
                    8.0, "glasses", LocalTime.of(14, 0), LocalDate.of(2026, 1, 1), false
            );
            // Now is 10:00 AM on 2026-03-10
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 10, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            assertEquals(LocalDateTime.of(2026, 3, 10, 14, 0), next);
        }

        @Test
        @DisplayName("Reminder time already passed today -> triggers tomorrow at reminderTime")
        void testReminderTimeAlreadyPassedToday() {
            Habit habit = new Habit(
                    1L, "Morning Meditation", HabitType.TIMED, Schedule.daily(),
                    15.0, "min", LocalTime.of(8, 30), LocalDate.of(2026, 1, 1), false
            );
            // Now is 10:00 AM (8:30 AM already passed)
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 10, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            // Should be tomorrow 2026-03-11 at 8:30 AM
            assertEquals(LocalDateTime.of(2026, 3, 11, 8, 30), next);
        }
    }

    @Nested
    @DisplayName("SPECIFIC_DAYS habits")
    class SpecificDaysHabits {

        @Test
        @DisplayName("Next scheduled weekday (Mon, Wed, Fri) when now is Tuesday -> Wednesday")
        void testNextScheduledWeekday() {
            Habit habit = new Habit(
                    2L, "Gym Workout", HabitType.YES_NO,
                    Schedule.specificDays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                    1.0, null, LocalTime.of(18, 0), LocalDate.of(2026, 1, 1), false
            );
            // 2026-03-10 is a TUESDAY at 09:00 AM
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 9, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            // Next scheduled is Wednesday 2026-03-11 at 18:00
            assertEquals(LocalDateTime.of(2026, 3, 11, 18, 0), next);
        }

        @Test
        @DisplayName("Scheduled day today, but time passed -> skips to following scheduled day")
        void testScheduledDayTodayTimePassed() {
            Habit habit = new Habit(
                    2L, "Gym Workout", HabitType.YES_NO,
                    Schedule.specificDays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                    1.0, null, LocalTime.of(18, 0), LocalDate.of(2026, 1, 1), false
            );
            // 2026-03-13 is a FRIDAY at 19:00 (18:00 already passed)
            LocalDateTime now = LocalDateTime.of(2026, 3, 13, 19, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            // Should skip Saturday & Sunday and trigger next Monday 2026-03-16 at 18:00
            assertEquals(LocalDateTime.of(2026, 3, 16, 18, 0), next);
        }
    }

    @Nested
    @DisplayName("TIMES_PER_WEEK habits")
    class TimesPerWeekHabits {

        @Test
        @DisplayName("Target not met -> reminds daily until weekly target met")
        void testTargetNotMetDailyReminders() {
            Habit habit = new Habit(
                    3L, "Read Book", HabitType.COUNTABLE,
                    Schedule.timesPerWeek(3),
                    20.0, "pages", LocalTime.of(20, 0), LocalDate.of(2026, 1, 1), false
            );
            // 2026-03-09 is Monday of the week. Now is 21:00 (20:00 passed)
            LocalDateTime now = LocalDateTime.of(2026, 3, 9, 21, 0);

            // 0 logs so far this week -> target 3 not met
            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            // Should remind tomorrow Tuesday 2026-03-10 at 20:00
            assertEquals(LocalDateTime.of(2026, 3, 10, 20, 0), next);
        }

        @Test
        @DisplayName("Target met -> skips rest of week and schedules next Monday")
        void testTargetMetSkipsToNextMonday() {
            Habit habit = new Habit(
                    3L, "Read Book", HabitType.COUNTABLE,
                    Schedule.timesPerWeek(2),
                    20.0, "pages", LocalTime.of(20, 0), LocalDate.of(2026, 1, 1), false
            );
            // Week is Monday 2026-03-09 to Sunday 2026-03-15
            // User completed Monday (2026-03-09) and Tuesday (2026-03-10) -> 2/2 met!
            List<HabitLog> logs = List.of(
                    new HabitLog(3L, LocalDate.of(2026, 3, 9), 20.0),
                    new HabitLog(3L, LocalDate.of(2026, 3, 10), 25.0)
            );

            // Now is Wednesday 2026-03-11 at 10:00 AM
            LocalDateTime now = LocalDateTime.of(2026, 3, 11, 10, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, logs, now);

            // Since target is 2/2 met, next reminder is Monday of the NEXT week (2026-03-16) at 20:00!
            assertEquals(LocalDateTime.of(2026, 3, 16, 20, 0), next);
        }
    }

    @Nested
    @DisplayName("Edge Cases & Inactive Habits")
    class EdgeCases {

        @Test
        @DisplayName("No reminderTime -> returns null")
        void testNullReminderTime() {
            Habit habit = new Habit(
                    4L, "No Reminder", HabitType.YES_NO, Schedule.daily(),
                    1.0, null, null, LocalDate.of(2026, 1, 1), false
            );
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 10, 0);

            assertNull(ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now));
        }

        @Test
        @DisplayName("Archived habit -> returns null")
        void testArchivedHabit() {
            Habit habit = new Habit(
                    5L, "Archived Habit", HabitType.YES_NO, Schedule.daily(),
                    1.0, null, LocalTime.of(9, 0), LocalDate.of(2026, 1, 1), true
            );
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 8, 0);

            assertNull(ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now));
        }

        @Test
        @DisplayName("Days before createdDate are never scheduled")
        void testBeforeCreatedDate() {
            // Created on 2026-03-15
            Habit habit = new Habit(
                    6L, "Future Habit", HabitType.YES_NO, Schedule.daily(),
                    1.0, null, LocalTime.of(9, 0), LocalDate.of(2026, 3, 15), false
            );
            // Now is 2026-03-10
            LocalDateTime now = LocalDateTime.of(2026, 3, 10, 8, 0);

            LocalDateTime next = ReminderCalculator.computeNextTrigger(habit, Collections.emptyList(), now);

            // First eligible trigger is createdDate: 2026-03-15 at 09:00
            assertEquals(LocalDateTime.of(2026, 3, 15, 9, 0), next);
        }
    }
}
