package com.devansh.core.model;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object describing when a habit is due.
 *
 * Three factory methods enforce the correct creation pattern:
 * - {@link #daily()}              → DAILY
 * - {@link #specificDays(Set)}    → SPECIFIC_DAYS with the chosen days
 * - {@link #timesPerWeek(int)}    → TIMES_PER_WEEK with count n
 *
 * ISO weeks run Monday to Sunday (java.time default).
 */
public final class Schedule {

    /** Which scheduling strategy this schedule uses. */
    private final ScheduleType type;

    /**
     * The set of days the habit is due.
     * Only meaningful for SPECIFIC_DAYS; empty for others.
     */
    private final Set<DayOfWeek> daysOfWeek;

    /**
     * How many times per week the habit should be completed.
     * Only meaningful for TIMES_PER_WEEK; 0 for others.
     */
    private final int timesPerWeek;

    // ── Private constructor ──────────────────────────────────────────
    private Schedule(ScheduleType type, Set<DayOfWeek> daysOfWeek, int timesPerWeek) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        // Defensive copy so external code cannot mutate internal state
        this.daysOfWeek = daysOfWeek.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(daysOfWeek));
        this.timesPerWeek = timesPerWeek;
    }

    // ── Factory methods ──────────────────────────────────────────────

    /** Create a DAILY schedule (every day). */
    public static Schedule daily() {
        return new Schedule(ScheduleType.DAILY, Collections.emptySet(), 0);
    }

    /**
     * Create a SPECIFIC_DAYS schedule for the given days of the week.
     *
     * @param days non-empty set of days
     * @throws IllegalArgumentException if {@code days} is empty
     */
    public static Schedule specificDays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("specificDays requires at least one day");
        }
        return new Schedule(ScheduleType.SPECIFIC_DAYS, days, 0);
    }

    /**
     * Create a TIMES_PER_WEEK schedule with the given target count.
     *
     * @param n number of times per week (must be 1–7)
     * @throws IllegalArgumentException if {@code n} is not between 1 and 7
     */
    public static Schedule timesPerWeek(int n) {
        if (n < 1 || n > 7) {
            throw new IllegalArgumentException("timesPerWeek must be 1-7, got " + n);
        }
        return new Schedule(ScheduleType.TIMES_PER_WEEK, Collections.emptySet(), n);
    }

    // ── Getters ──────────────────────────────────────────────────────

    public ScheduleType getType() {
        return type;
    }

    /** Returns an unmodifiable set; empty for non-SPECIFIC_DAYS schedules. */
    public Set<DayOfWeek> getDaysOfWeek() {
        return daysOfWeek;
    }

    /** Returns 0 for non-TIMES_PER_WEEK schedules. */
    public int getTimesPerWeek() {
        return timesPerWeek;
    }

    // ── Object overrides ─────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Schedule that)) return false;
        return timesPerWeek == that.timesPerWeek
                && type == that.type
                && Objects.equals(daysOfWeek, that.daysOfWeek);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, daysOfWeek, timesPerWeek);
    }

    @Override
    public String toString() {
        return switch (type) {
            case DAILY -> "Schedule{DAILY}";
            case SPECIFIC_DAYS -> "Schedule{SPECIFIC_DAYS=" + daysOfWeek + "}";
            case TIMES_PER_WEEK -> "Schedule{TIMES_PER_WEEK=" + timesPerWeek + "}";
        };
    }
}
