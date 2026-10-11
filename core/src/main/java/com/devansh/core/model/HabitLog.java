package com.devansh.core.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a single log entry for a habit on a specific date.
 *
 * Each habit has at most one log per date. If the user edits a past day,
 * the existing log is replaced (the latest value wins).
 *
 * Completion is derived: value >= habit.target means complete.
 */
public final class HabitLog {

    /** Which habit this log belongs to (foreign key in Room). */
    private final long habitId;

    /** The calendar date this log is for (never null). */
    private final LocalDate date;

    /**
     * The logged value:
     * - YES_NO: 0.0 or 1.0
     * - COUNTABLE: number of units (e.g. pages read)
     * - TIMED: number of minutes
     */
    private final double value;

    // ── Constructor ──────────────────────────────────────────────────

    public HabitLog(long habitId, LocalDate date, double value) {
        this.habitId = habitId;
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.value = value;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public long getHabitId()    { return habitId; }
    public LocalDate getDate()  { return date; }
    public double getValue()    { return value; }

    // ── Object overrides ─────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HabitLog that)) return false;
        return habitId == that.habitId && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(habitId, date);
    }

    @Override
    public String toString() {
        return "HabitLog{habitId=" + habitId + ", date=" + date + ", value=" + value + "}";
    }
}
