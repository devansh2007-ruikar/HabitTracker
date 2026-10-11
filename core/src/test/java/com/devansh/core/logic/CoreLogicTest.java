package com.devansh.core.logic;

import com.devansh.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for the :core module.
 *
 * Covers ScheduleRules, StreakCalculator, and StatsCalculator across
 * all schedule types (DAILY, SPECIFIC_DAYS, TIMES_PER_WEEK), habit
 * types (YES_NO, COUNTABLE, TIMED), edge cases (grace days, past edits,
 * week boundaries, duplicates, leap years), and the exact example from
 * the GDG task description.
 */
class CoreLogicTest {

    // ═══════════════════════════════════════════════════════════════════
    //  Helper factories — keep tests readable
    // ═══════════════════════════════════════════════════════════════════

    /** Creates a daily countable habit (e.g. "Read 20 pages"). */
    private static Habit dailyCountable(long id, String name, double target,
                                        String unit, LocalDate created) {
        return new Habit(id, name, HabitType.COUNTABLE, Schedule.daily(),
                target, unit, null, created, false);
    }

    /** Creates a yes/no habit scheduled on specific days. */
    private static Habit weekdayYesNo(long id, String name, Set<DayOfWeek> days,
                                      LocalDate created) {
        return new Habit(id, name, HabitType.YES_NO, Schedule.specificDays(days),
                1, null, null, created, false);
    }

    /** Creates a daily yes/no habit. */
    private static Habit dailyYesNo(long id, String name, LocalDate created) {
        return new Habit(id, name, HabitType.YES_NO, Schedule.daily(),
                1, null, null, created, false);
    }

    /** Creates a timed daily habit (target in minutes). */
    private static Habit dailyTimed(long id, String name, double targetMinutes,
                                    LocalDate created) {
        return new Habit(id, name, HabitType.TIMED, Schedule.daily(),
                targetMinutes, "min", null, created, false);
    }

    /** Creates a times-per-week yes/no habit. */
    private static Habit timesPerWeekYesNo(long id, String name, int n,
                                           LocalDate created) {
        return new Habit(id, name, HabitType.YES_NO, Schedule.timesPerWeek(n),
                1, null, null, created, false);
    }

    /** Shorthand for creating a log entry. */
    private static HabitLog log(long habitId, LocalDate date, double value) {
        return new HabitLog(habitId, date, value);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 1: GDG task exact example
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 1 – GDG task exact example")
    class GdgTaskExample {

        // Scenario: Mon 2024-01-08 to Wed 2024-01-10
        // "Read" = countable, target 20, daily schedule
        // "Gym"  = yes/no, Mon/Wed/Fri schedule
        //
        // Initial logs (Mon, Tue, Wed all logged, but Mon Read = 15 pages):
        //   Mon: Read 15 (incomplete), Gym done
        //   Tue: Read 20 (complete),   Gym not scheduled
        //   Wed: Read 25 (complete),   Gym done
        //
        // Expected: streak(read) = 2 (Tue + Wed), streak(gym) = 2 (Mon + Wed)
        //
        // Edit: correct Monday Read to 20 → streak(read) = 3

        final LocalDate MON = LocalDate.of(2024, 1, 8);  // Monday
        final LocalDate TUE = LocalDate.of(2024, 1, 9);
        final LocalDate WED = LocalDate.of(2024, 1, 10); // today

        final Habit read = dailyCountable(1, "Read", 20, "pages", MON);
        final Habit gym = weekdayYesNo(2, "Gym",
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), MON);

        @Test
        @DisplayName("Read streak = 2 when Monday is incomplete (15 < 20)")
        void readStreakBeforeEdit() {
            List<HabitLog> readLogs = List.of(
                    log(1, MON, 15),  // incomplete
                    log(1, TUE, 20),  // complete
                    log(1, WED, 25)   // complete
            );
            assertEquals(2, StreakCalculator.currentStreak(read, readLogs, WED));
        }

        @Test
        @DisplayName("Gym streak = 2 (Mon done + Tue not scheduled + Wed done)")
        void gymStreak() {
            List<HabitLog> gymLogs = List.of(
                    log(2, MON, 1),  // complete
                    log(2, WED, 1)   // complete
            );
            // Tue is not scheduled for gym, so it doesn't break the streak
            assertEquals(2, StreakCalculator.currentStreak(gym, gymLogs, WED));
        }

        @Test
        @DisplayName("Edit Monday Read to 20 → streak becomes 3")
        void readStreakAfterEdit() {
            List<HabitLog> readLogs = List.of(
                    log(1, MON, 20),  // corrected to 20 → now complete
                    log(1, TUE, 20),
                    log(1, WED, 25)
            );
            assertEquals(3, StreakCalculator.currentStreak(read, readLogs, WED));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 2: Missed day breaks a daily streak
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 2 – A missed day breaks a daily streak")
    void missedDayBreaksDailyStreak() {
        LocalDate start = LocalDate.of(2024, 3, 1); // Friday
        Habit h = dailyYesNo(1, "Meditate", start);
        // Completed Fri, Sat, missed Sun, completed Mon
        List<HabitLog> logs = List.of(
                log(1, start, 1),
                log(1, start.plusDays(1), 1),
                // start.plusDays(2) = Sunday → MISSED
                log(1, start.plusDays(3), 1)  // Monday
        );
        LocalDate today = start.plusDays(3); // Monday
        // Streak should be 1 (only Monday), because Sunday breaks it
        assertEquals(1, StreakCalculator.currentStreak(h, logs, today));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 3: Unscheduled days don't break a weekday streak
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 3 – Unscheduled days don't break a SPECIFIC_DAYS streak")
    void unscheduledDaysDontBreakWeekdayStreak() {
        LocalDate mon = LocalDate.of(2024, 3, 4); // Monday
        // Habit only on Mon and Fri
        Habit h = weekdayYesNo(1, "Yoga", EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), mon);
        List<HabitLog> logs = List.of(
                log(1, mon, 1),                     // Mon complete
                log(1, mon.plusDays(4), 1)            // Fri complete
        );
        LocalDate today = mon.plusDays(4); // Friday
        // Tue, Wed, Thu are not scheduled → skipped → streak = 2
        assertEquals(2, StreakCalculator.currentStreak(h, logs, today));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 4: Partial progress on countable doesn't count; exact/over counts
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 4 – Partial, exact, and over-target for countable habits")
    class CountableThresholds {

        final LocalDate day = LocalDate.of(2024, 5, 1);
        final Habit h = dailyCountable(1, "Read", 20, "pages", day);

        @Test
        @DisplayName("19 pages (partial) is NOT complete")
        void partialNotComplete() {
            assertFalse(ScheduleRules.isComplete(h, log(1, day, 19)));
        }

        @Test
        @DisplayName("20 pages (exact) IS complete")
        void exactIsComplete() {
            assertTrue(ScheduleRules.isComplete(h, log(1, day, 20)));
        }

        @Test
        @DisplayName("25 pages (over) IS complete")
        void overIsComplete() {
            assertTrue(ScheduleRules.isComplete(h, log(1, day, 25)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 5: Editing a past day (missed→completed restores, completed→partial breaks)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 5 – Editing a past day affects the streak")
    class PastDayEdits {

        final LocalDate mon = LocalDate.of(2024, 6, 3);  // Monday
        final LocalDate tue = LocalDate.of(2024, 6, 4);
        final LocalDate wed = LocalDate.of(2024, 6, 5);
        final Habit h = dailyCountable(1, "Read", 20, "pages", mon);

        @Test
        @DisplayName("Missed Monday → completing it restores 3-day streak")
        void missedToCompletedRestoresStreak() {
            // Before edit: Mon missed, Tue+Wed done → streak = 2
            List<HabitLog> before = List.of(
                    log(1, tue, 20),
                    log(1, wed, 20)
            );
            assertEquals(2, StreakCalculator.currentStreak(h, before, wed));

            // After edit: Mon corrected to 20 → streak = 3
            List<HabitLog> after = List.of(
                    log(1, mon, 20),  // edited
                    log(1, tue, 20),
                    log(1, wed, 20)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, after, wed));
        }

        @Test
        @DisplayName("Completed Tuesday → editing to partial (10) breaks streak")
        void completedToPartialBreaksStreak() {
            // Before edit: all three days complete → streak = 3
            List<HabitLog> before = List.of(
                    log(1, mon, 20),
                    log(1, tue, 20),
                    log(1, wed, 20)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, before, wed));

            // After edit: Tue reduced to 10 → streak = 1 (only Wed)
            List<HabitLog> after = List.of(
                    log(1, mon, 20),
                    log(1, tue, 10),  // now partial → breaks streak
                    log(1, wed, 20)
            );
            assertEquals(1, StreakCalculator.currentStreak(h, after, wed));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 6: Today not done yet doesn't reset streak; done adds 1
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 6 – Grace day: today incomplete doesn't break streak")
    class TodayGraceDay {

        final LocalDate mon = LocalDate.of(2024, 7, 1);
        final LocalDate tue = LocalDate.of(2024, 7, 2);
        final LocalDate wed = LocalDate.of(2024, 7, 3);
        final Habit h = dailyYesNo(1, "Journal", mon);

        @Test
        @DisplayName("Mon+Tue done, Wed (today) not yet → streak = 2")
        void todayNotDone() {
            List<HabitLog> logs = List.of(
                    log(1, mon, 1),
                    log(1, tue, 1)
                    // Wed (today) not logged
            );
            assertEquals(2, StreakCalculator.currentStreak(h, logs, wed));
        }

        @Test
        @DisplayName("Mon+Tue done, Wed (today) done → streak = 3")
        void todayDone() {
            List<HabitLog> logs = List.of(
                    log(1, mon, 1),
                    log(1, tue, 1),
                    log(1, wed, 1)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, logs, wed));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 7: Midnight boundary – toLogicalDate
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 7 – Midnight boundary: toLogicalDate converts correctly")
    class MidnightBoundary {

        @Test
        @DisplayName("23:59 on Jan 15 → logical date = Jan 15")
        void lateNightSameDay() {
            LocalDateTime dt = LocalDateTime.of(2024, 1, 15, 23, 59);
            assertEquals(LocalDate.of(2024, 1, 15), ScheduleRules.toLogicalDate(dt));
        }

        @Test
        @DisplayName("00:01 on Jan 16 → logical date = Jan 16")
        void earlyMorningNextDay() {
            LocalDateTime dt = LocalDateTime.of(2024, 1, 16, 0, 1);
            assertEquals(LocalDate.of(2024, 1, 16), ScheduleRules.toLogicalDate(dt));
        }

        @Test
        @DisplayName("Exactly midnight 00:00 on Jan 16 → logical date = Jan 16")
        void exactlyMidnight() {
            LocalDateTime dt = LocalDateTime.of(2024, 1, 16, 0, 0);
            assertEquals(LocalDate.of(2024, 1, 16), ScheduleRules.toLogicalDate(dt));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 8: N times per week – met/unmet weeks, current week grace
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 8 – TIMES_PER_WEEK streak counting")
    class TimesPerWeekStreak {

        // Habit: 3 times per week, created Mon Jan 1 2024
        final LocalDate created = LocalDate.of(2024, 1, 1); // Monday
        final Habit h = timesPerWeekYesNo(1, "Workout", 3, created);

        @Test
        @DisplayName("Two fully met weeks → streak = 2")
        void twoMetWeeks() {
            // Week 1 (Jan 1-7): 3 completions
            // Week 2 (Jan 8-14): 3 completions
            List<HabitLog> logs = List.of(
                    log(1, LocalDate.of(2024, 1, 1), 1),
                    log(1, LocalDate.of(2024, 1, 3), 1),
                    log(1, LocalDate.of(2024, 1, 5), 1),
                    log(1, LocalDate.of(2024, 1, 8), 1),
                    log(1, LocalDate.of(2024, 1, 10), 1),
                    log(1, LocalDate.of(2024, 1, 12), 1)
            );
            // Today is end of week 2
            LocalDate today = LocalDate.of(2024, 1, 14); // Sunday
            assertEquals(2, StreakCalculator.currentStreak(h, logs, today));
        }

        @Test
        @DisplayName("Unmet past week breaks the streak")
        void unmetPastWeekBreaks() {
            // Week 1 (Jan 1-7): 3 completions
            // Week 2 (Jan 8-14): only 1 completion → BREAKS
            // Week 3 (Jan 15-21): 3 completions
            List<HabitLog> logs = List.of(
                    log(1, LocalDate.of(2024, 1, 1), 1),
                    log(1, LocalDate.of(2024, 1, 3), 1),
                    log(1, LocalDate.of(2024, 1, 5), 1),
                    log(1, LocalDate.of(2024, 1, 9), 1),  // only 1 in week 2
                    log(1, LocalDate.of(2024, 1, 15), 1),
                    log(1, LocalDate.of(2024, 1, 17), 1),
                    log(1, LocalDate.of(2024, 1, 19), 1)
            );
            LocalDate today = LocalDate.of(2024, 1, 21);
            // Only week 3 counts (week 2 broke it)
            assertEquals(1, StreakCalculator.currentStreak(h, logs, today));
        }

        @Test
        @DisplayName("Current week not yet met → grace week, doesn't break streak")
        void currentWeekGrace() {
            // Week 1 (Jan 1-7): 3 completions
            // Week 2 (Jan 8-14): only 1 so far, but it's the current week
            List<HabitLog> logs = List.of(
                    log(1, LocalDate.of(2024, 1, 1), 1),
                    log(1, LocalDate.of(2024, 1, 3), 1),
                    log(1, LocalDate.of(2024, 1, 5), 1),
                    log(1, LocalDate.of(2024, 1, 8), 1)  // only 1 so far this week
            );
            LocalDate today = LocalDate.of(2024, 1, 9); // Tuesday of week 2
            // Current week not met yet, but grace → streak = 1 (week 1)
            assertEquals(1, StreakCalculator.currentStreak(h, logs, today));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 9: Habit created mid-week – days before createdDate don't
    //           count as missed
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 9 – Days before createdDate are not scheduled and don't count as missed")
    void createdMidWeekNoPenalty() {
        // Habit created on Wednesday Jan 3 2024
        LocalDate wed = LocalDate.of(2024, 1, 3);
        Habit h = dailyYesNo(1, "Stretch", wed);

        // Logs: Wed, Thu, Fri all complete
        List<HabitLog> logs = List.of(
                log(1, wed, 1),
                log(1, wed.plusDays(1), 1),
                log(1, wed.plusDays(2), 1)
        );

        LocalDate today = wed.plusDays(2); // Friday
        // Mon and Tue are before createdDate → not scheduled → streak = 3
        assertEquals(3, StreakCalculator.currentStreak(h, logs, today));

        // Also verify isScheduled returns false for days before creation
        assertFalse(ScheduleRules.isScheduled(h, wed.minusDays(1)));
        assertFalse(ScheduleRules.isScheduled(h, wed.minusDays(2)));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 10: Longest streak > current streak after a break
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 10 – Longest streak is greater than current after a break")
    void longestGreaterThanCurrent() {
        LocalDate start = LocalDate.of(2024, 2, 1); // Thursday
        Habit h = dailyYesNo(1, "Walk", start);

        // 5-day streak (Feb 1–5), then miss Feb 6, then 2-day streak (Feb 7–8)
        List<HabitLog> logs = List.of(
                log(1, start, 1),
                log(1, start.plusDays(1), 1),
                log(1, start.plusDays(2), 1),
                log(1, start.plusDays(3), 1),
                log(1, start.plusDays(4), 1),
                // start.plusDays(5) = Feb 6 → MISSED
                log(1, start.plusDays(6), 1),
                log(1, start.plusDays(7), 1)
        );
        LocalDate today = start.plusDays(7); // Feb 8

        assertEquals(2, StreakCalculator.currentStreak(h, logs, today));
        assertEquals(5, StreakCalculator.longestStreak(h, logs, today));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 11: Timed habit – minutes >= target completes
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 11 – Timed habit: minutes >= target")
    class TimedHabit {

        final LocalDate day = LocalDate.of(2024, 4, 1);
        final Habit h = dailyTimed(1, "Meditate", 15, day);

        @Test
        @DisplayName("14 minutes is NOT complete")
        void under() {
            assertFalse(ScheduleRules.isComplete(h, log(1, day, 14)));
        }

        @Test
        @DisplayName("15 minutes IS complete")
        void exact() {
            assertTrue(ScheduleRules.isComplete(h, log(1, day, 15)));
        }

        @Test
        @DisplayName("30 minutes IS complete")
        void over() {
            assertTrue(ScheduleRules.isComplete(h, log(1, day, 30)));
        }

        @Test
        @DisplayName("Timed streak counts correctly across 3 days")
        void timedStreak() {
            List<HabitLog> logs = List.of(
                    log(1, day, 15),
                    log(1, day.plusDays(1), 20),
                    log(1, day.plusDays(2), 15)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, logs, day.plusDays(2)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 12: Completion rate maths across schedules
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 12 – Completion rate calculations")
    class CompletionRate {

        @Test
        @DisplayName("Daily habit: 2 of 3 past days complete → rate = 2/3")
        void dailyRate() {
            LocalDate mon = LocalDate.of(2024, 8, 5);
            Habit h = dailyYesNo(1, "Drink water", mon);
            List<HabitLog> logs = List.of(
                    log(1, mon, 1),
                    // Tue missed
                    log(1, mon.plusDays(2), 1)
            );
            // Range = Mon to Wed, today = Thu (all three are past)
            LocalDate today = mon.plusDays(3);
            double rate = StatsCalculator.completionRate(h, logs, mon, mon.plusDays(2), today);
            assertEquals(2.0 / 3.0, rate, 0.001);
        }

        @Test
        @DisplayName("SPECIFIC_DAYS habit: completion rate excludes non-scheduled days")
        void weekdayRate() {
            LocalDate mon = LocalDate.of(2024, 8, 5);
            // Only Mon, Wed, Fri
            Habit h = weekdayYesNo(1, "Gym",
                    EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), mon);
            List<HabitLog> logs = List.of(
                    log(1, mon, 1),            // Mon ✓
                    log(1, mon.plusDays(2), 1)  // Wed ✓
                    // Fri missed
            );
            // Range Mon to Sun, today = next Monday (all days are past)
            LocalDate today = mon.plusDays(7);
            double rate = StatsCalculator.completionRate(h, logs, mon, mon.plusDays(6), today);
            // 3 scheduled days (Mon, Wed, Fri), 2 complete → 2/3
            assertEquals(2.0 / 3.0, rate, 0.001);
        }

        @Test
        @DisplayName("Future days are excluded from rate calculation")
        void futureDaysExcluded() {
            LocalDate mon = LocalDate.of(2024, 8, 5);
            Habit h = dailyYesNo(1, "Run", mon);
            List<HabitLog> logs = List.of(
                    log(1, mon, 1)
            );
            // Range Mon to Fri, today = Tue → only Mon is counted (Tue today not done)
            LocalDate today = mon.plusDays(1);
            double rate = StatsCalculator.completionRate(h, logs, mon, mon.plusDays(4), today);
            // Mon complete / Mon scheduled = 1.0
            assertEquals(1.0, rate, 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 13: Empty logs → streak 0, rate 0
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 13 – Empty logs produce streak 0 and rate 0.0")
    void emptyLogs() {
        LocalDate day = LocalDate.of(2024, 9, 1);
        Habit h = dailyYesNo(1, "Floss", day);
        List<HabitLog> logs = Collections.emptyList();

        assertEquals(0, StreakCalculator.currentStreak(h, logs, day));
        assertEquals(0, StreakCalculator.longestStreak(h, logs, day));
        assertEquals(0.0, StatsCalculator.completionRate(h, logs, day, day, day));
        assertEquals(0, StatsCalculator.totalCompletions(h, logs));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 14: Week boundaries (Sun→Mon) and leap year
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TEST 14 – Week and year boundaries")
    class Boundaries {

        @Test
        @DisplayName("Streak survives across Sunday → Monday boundary")
        void sundayToMonday() {
            LocalDate sat = LocalDate.of(2024, 3, 2);   // Saturday
            LocalDate sun = LocalDate.of(2024, 3, 3);   // Sunday
            LocalDate mon = LocalDate.of(2024, 3, 4);   // Monday

            Habit h = dailyYesNo(1, "Read", sat);
            List<HabitLog> logs = List.of(
                    log(1, sat, 1),
                    log(1, sun, 1),
                    log(1, mon, 1)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, logs, mon));
        }

        @Test
        @DisplayName("Streak survives across Dec 31 → Jan 1 year boundary")
        void yearBoundary() {
            LocalDate dec31 = LocalDate.of(2023, 12, 31);
            LocalDate jan1 = LocalDate.of(2024, 1, 1);

            Habit h = dailyYesNo(1, "Meditate", dec31);
            List<HabitLog> logs = List.of(
                    log(1, dec31, 1),
                    log(1, jan1, 1)
            );
            assertEquals(2, StreakCalculator.currentStreak(h, logs, jan1));
        }

        @Test
        @DisplayName("Feb 29 leap year day is a valid scheduled and loggable day")
        void leapYear() {
            LocalDate feb28 = LocalDate.of(2024, 2, 28);
            LocalDate feb29 = LocalDate.of(2024, 2, 29);
            LocalDate mar1 = LocalDate.of(2024, 3, 1);

            Habit h = dailyYesNo(1, "Walk", feb28);
            List<HabitLog> logs = List.of(
                    log(1, feb28, 1),
                    log(1, feb29, 1),
                    log(1, mar1, 1)
            );
            assertEquals(3, StreakCalculator.currentStreak(h, logs, mar1));
            assertTrue(ScheduleRules.isScheduled(h, feb29));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEST 15: Duplicate logs for the same day → last value wins
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TEST 15 – Duplicate logs: the last value for a date wins")
    void duplicateLogsLastWins() {
        LocalDate day = LocalDate.of(2024, 10, 1);
        Habit h = dailyCountable(1, "Read", 20, "pages", day);

        // Two logs for the same day: first = 10 (incomplete), second = 25 (complete)
        List<HabitLog> logs = List.of(
                log(1, day, 10),  // first entry
                log(1, day, 25)   // latest entry → should win
        );
        assertEquals(1, StreakCalculator.currentStreak(h, logs, day));
        assertEquals(1, StatsCalculator.totalCompletions(h, logs));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BONUS: DayStatus map and totalCompletions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BONUS – DayStatus map and totalCompletions")
    class DayStatusMapAndTotals {

        @Test
        @DisplayName("DayStatus map assigns correct status to each day")
        void dayStatusMap() {
            LocalDate mon = LocalDate.of(2024, 11, 4); // Monday
            // Habit: Mon/Wed/Fri only
            Habit h = weekdayYesNo(1, "Gym",
                    EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), mon);

            List<HabitLog> logs = List.of(
                    log(1, mon, 1)  // Mon complete
                    // Wed will be missed, Fri is future
            );

            // today = Thursday → Mon past, Tue past (not sched), Wed past (missed),
            //                     Thu (not sched), Fri (future), Sat (future)
            LocalDate today = mon.plusDays(3); // Thursday
            Map<LocalDate, DayStatus> map = StatsCalculator.buildDayStatusMap(
                    h, logs, mon, mon.plusDays(5), today);

            assertEquals(DayStatus.COMPLETE, map.get(mon));
            assertEquals(DayStatus.NOT_SCHEDULED, map.get(mon.plusDays(1))); // Tue
            assertEquals(DayStatus.MISSED, map.get(mon.plusDays(2)));        // Wed
            assertEquals(DayStatus.NOT_SCHEDULED, map.get(mon.plusDays(3))); // Thu
            assertEquals(DayStatus.FUTURE, map.get(mon.plusDays(4)));        // Fri
            assertEquals(DayStatus.FUTURE, map.get(mon.plusDays(5)));        // Sat
        }

        @Test
        @DisplayName("totalCompletions counts only fully completed logs")
        void totalCompletionsCount() {
            LocalDate day = LocalDate.of(2024, 11, 1);
            Habit h = dailyCountable(1, "Read", 20, "pages", day);
            List<HabitLog> logs = List.of(
                    log(1, day, 20),            // complete
                    log(1, day.plusDays(1), 10), // partial → NOT counted
                    log(1, day.plusDays(2), 25), // complete
                    log(1, day.plusDays(3), 0)   // zero → NOT counted
            );
            assertEquals(2, StatsCalculator.totalCompletions(h, logs));
        }

        @Test
        @DisplayName("Partial log shows PARTIAL status in day map")
        void partialStatus() {
            LocalDate day = LocalDate.of(2024, 11, 1);
            Habit h = dailyCountable(1, "Read", 20, "pages", day);
            List<HabitLog> logs = List.of(
                    log(1, day, 10)  // partial
            );
            Map<LocalDate, DayStatus> map = StatsCalculator.buildDayStatusMap(
                    h, logs, day, day, day);
            assertEquals(DayStatus.PARTIAL, map.get(day));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BONUS: ScheduleRules edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BONUS – YES_NO habit always has target = 1 regardless of constructor arg")
    void yesNoTargetAlwaysOne() {
        Habit h = new Habit(1, "Test", HabitType.YES_NO, Schedule.daily(),
                999, null, null, LocalDate.of(2024, 1, 1), false);
        assertEquals(1.0, h.getTarget());
    }

    @Test
    @DisplayName("BONUS – Schedule.specificDays rejects empty set")
    void specificDaysRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Schedule.specificDays(EnumSet.noneOf(DayOfWeek.class)));
    }

    @Test
    @DisplayName("BONUS – Schedule.timesPerWeek rejects 0 and 8")
    void timesPerWeekBounds() {
        assertThrows(IllegalArgumentException.class, () -> Schedule.timesPerWeek(0));
        assertThrows(IllegalArgumentException.class, () -> Schedule.timesPerWeek(8));
    }
}
